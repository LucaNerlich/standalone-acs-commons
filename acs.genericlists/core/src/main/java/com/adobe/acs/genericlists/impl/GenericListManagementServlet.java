package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.api.GenericList;
import com.adobe.acs.genericlists.api.GenericListLocale;
import com.adobe.acs.genericlists.api.GenericListSchema;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationStatus;
import com.day.cq.replication.Replicator;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.jcr.Session;
import javax.servlet.Servlet;
import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Permission-aware management API used by the modern Generic Lists console.
 *
 * <p>The API intentionally uses the request's resolver, never a service user. Repository ACLs remain authoritative
 * and all mutations are restricted to configured roots. POST requests are expected to pass AEM's standard CSRF
 * filter.</p>
 */
@Designate(ocd = GenericListManagementServlet.Config.class)
@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.methods=" + HttpConstants.METHOD_POST
        })
@SlingServletPaths("/bin/acs-genericlists/lists")
public final class GenericListManagementServlet extends SlingAllMethodsServlet {

    private static final String CANONICAL_RT = GenericListImpl.RT_KEY_VALUE_LIST;

    @ObjectClassDefinition(
            name = "ACS Generic Lists - Management API",
            description = "Roots and limits for the author-only Generic Lists management console/API.")
    public @interface Config {
        @AttributeDefinition(name = "Managed roots", description = "Only paths below these roots may be changed.")
        String[] managed_roots() default {"/content/generic-lists", "/content/config"};

        @AttributeDefinition(name = "Usage search roots", description = "Roots searched by the where-used action.")
        String[] usage_search_roots() default {"/content"};

        @AttributeDefinition(name = "Maximum API results")
        int max_results() default 250;
    }

    private volatile List<String> managedRoots = List.of("/content/generic-lists", "/content/config");
    private volatile List<String> usageSearchRoots = List.of("/content");
    private volatile int maxResults = 250;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    private volatile Replicator replicator;

    @Activate
    void activate(final Config config) {
        managedRoots = normalizeRoots(config.managed_roots(), List.of("/content/generic-lists", "/content/config"));
        usageSearchRoots = normalizeRoots(config.usage_search_roots(), List.of("/content"));
        maxResults = Math.max(1, Math.min(config.max_results(), 2_000));
    }

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException {
        final String action = request.getParameter("action") == null ? "list" : request.getParameter("action");
        try {
            switch (action) {
                case "list" -> writeList(response, request);
                case "export" -> writeExport(response, request);
                case "usage" -> writeUsage(response, request);
                case "status" -> writeStatus(response, request);
                default -> writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, "Unknown action: " + action);
            }
        } catch (ManagementException ex) {
            writeError(response, ex.status(), ex.getMessage());
        } catch (JSONException ex) {
            throw new IOException("Unable to serialize Generic Lists response", ex);
        }
    }

    @Override
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException {
        final String action = request.getParameter("action");
        if (action == null || action.isBlank()) {
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, "action is required.");
            return;
        }
        try {
            switch (action) {
                case "create" -> create(request, response);
                case "delete" -> delete(request, response);
                case "copy" -> copy(request, response);
                case "move" -> move(request, response);
                case "import" -> importList(request, response);
                case "publish" -> replicate(request, response, ReplicationActionType.ACTIVATE);
                case "unpublish" -> replicate(request, response, ReplicationActionType.DEACTIVATE);
                default -> writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, "Unknown action: " + action);
            }
        } catch (ManagementException ex) {
            writeError(response, ex.status(), ex.getMessage());
        } catch (JSONException ex) {
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, "Invalid JSON payload: " + ex.getMessage());
        } catch (PersistenceException ex) {
            request.getResourceResolver().revert();
            writeError(response, SlingHttpServletResponse.SC_CONFLICT, ex.getMessage());
        } catch (ReplicationException ex) {
            writeError(response, SlingHttpServletResponse.SC_FORBIDDEN, ex.getMessage());
        }
    }

    private void writeList(final SlingHttpServletResponse response, final SlingHttpServletRequest request)
            throws IOException, JSONException, ManagementException {
        final String root = request.getParameter("root") == null ? managedRoots.getFirst() : request.getParameter("root");
        requireManagedPath(root);
        final String query = request.getParameter("q") == null ? "" : request.getParameter("q").trim().toLowerCase(Locale.ROOT);
        final int limit = boundedLimit(request.getParameter("limit"));
        final List<ListEntry> entries = discover(request.getResourceResolver(), root, query, limit);

        beginJson(response);
        final JSONWriter json = new JSONWriter(response.getWriter());
        json.object().key("root").value(root).key("count").value(entries.size()).key("lists").array();
        for (final ListEntry entry : entries) {
            json.object()
                    .key("path").value(entry.path())
                    .key("title").value(entry.title())
                    .key("description").value(entry.description())
                    .key("items").value(entry.items())
                    .key("valid").value(entry.valid())
                    .key("validationIssues").value(entry.validationIssues())
                    .key("published").value(entry.published())
                    .key("lastPublished").value(entry.lastPublished())
                    .endObject();
        }
        json.endArray().endObject();
    }

    private void writeExport(final SlingHttpServletResponse response, final SlingHttpServletRequest request)
            throws IOException, JSONException, ManagementException {
        final String path = required(request, "path");
        requireManagedPath(path);
        final Resource data = writableListResource(request.getResourceResolver(), path);
        final String format = request.getParameter("format") == null ? "json" : request.getParameter("format");
        if ("csv".equalsIgnoreCase(format)) {
            response.setContentType("text/csv");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=generic-list.csv");
            response.getWriter().write(toCsv(data));
            return;
        }
        beginJson(response);
        final JSONWriter json = new JSONWriter(response.getWriter());
        writeExportJson(json, data);
    }

    private void writeUsage(final SlingHttpServletResponse response, final SlingHttpServletRequest request)
            throws IOException, JSONException, ManagementException {
        final String path = required(request, "path");
        requireManagedPath(path);
        final List<String> usages = findUsages(request.getResourceResolver(), path, maxResults);
        beginJson(response);
        final JSONWriter json = new JSONWriter(response.getWriter());
        json.object().key("path").value(path).key("count").value(usages.size()).key("usages").array();
        for (final String usage : usages) {
            json.value(usage);
        }
        json.endArray().endObject();
    }

    private void writeStatus(final SlingHttpServletResponse response, final SlingHttpServletRequest request)
            throws IOException, JSONException, ManagementException {
        final String path = required(request, "path");
        requireManagedPath(path);
        final PublicationStatus status = publicationStatus(request.getResourceResolver(), path);
        beginJson(response);
        final JSONWriter json = new JSONWriter(response.getWriter());
        json.object()
                .key("path").value(path)
                .key("available").value(status.available())
                .key("published").value(status.published())
                .key("pending").value(status.pending())
                .key("lastPublished").value(status.lastPublished())
                .endObject();
    }

    private void create(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException, JSONException, PersistenceException, ManagementException {
        final String path = required(request, "path");
        requireManagedPath(path);
        final ResourceResolver resolver = request.getResourceResolver();
        if (resolver.getResource(path) != null) {
            throw new ManagementException(SlingHttpServletResponse.SC_CONFLICT, "A resource already exists at " + path + ".");
        }
        final Resource parent = ensureFolder(resolver, parentPath(path));
        final Map<String, Object> properties = new HashMap<>();
        properties.put("jcr:primaryType", "nt:unstructured");
        properties.put("sling:resourceType", CANONICAL_RT);
        putIfNonBlank(properties, GenericListSchema.PN_TITLE, request.getParameter("title"));
        putIfNonBlank(properties, GenericListSchema.PN_DESCRIPTION, request.getParameter("description"));
        resolver.create(parent, name(path), properties);
        resolver.commit();
        writeSuccess(response, "Created Generic List.", path);
    }

    private void delete(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException, JSONException, PersistenceException, ManagementException {
        final String path = required(request, "path");
        requireManagedPath(path);
        final Resource resource = request.getResourceResolver().getResource(path);
        if (resource == null) {
            throw new ManagementException(SlingHttpServletResponse.SC_NOT_FOUND, "List is not readable.");
        }
        request.getResourceResolver().delete(resource);
        request.getResourceResolver().commit();
        writeSuccess(response, "Deleted Generic List.", path);
    }

    private void copy(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException, JSONException, PersistenceException, ManagementException {
        final String source = required(request, "source");
        final String destination = required(request, "destination");
        requireManagedPath(source);
        requireManagedPath(destination);
        final ResourceResolver resolver = request.getResourceResolver();
        if (resolver.getResource(source) == null) {
            throw new ManagementException(SlingHttpServletResponse.SC_NOT_FOUND, "Source list is not readable.");
        }
        if (resolver.getResource(destination) != null) {
            throw new ManagementException(SlingHttpServletResponse.SC_CONFLICT, "Destination already exists.");
        }
        ensureFolder(resolver, parentPath(destination));
        resolver.copy(source, destination);
        resolver.commit();
        writeSuccess(response, "Copied Generic List.", destination);
    }

    private void move(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException, JSONException, PersistenceException, ManagementException {
        final String source = required(request, "source");
        final String destination = required(request, "destination");
        requireManagedPath(source);
        requireManagedPath(destination);
        final ResourceResolver resolver = request.getResourceResolver();
        if (resolver.getResource(source) == null) {
            throw new ManagementException(SlingHttpServletResponse.SC_NOT_FOUND, "Source list is not readable.");
        }
        if (resolver.getResource(destination) != null) {
            throw new ManagementException(SlingHttpServletResponse.SC_CONFLICT, "Destination already exists.");
        }
        ensureFolder(resolver, parentPath(destination));
        resolver.move(source, destination);
        resolver.commit();
        writeSuccess(response, "Moved Generic List.", destination);
    }

    private void importList(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException, JSONException, PersistenceException, ManagementException {
        final String path = required(request, "path");
        requireManagedPath(path);
        final String format = request.getParameter("format") == null ? "json" : request.getParameter("format");
        final String body = read(request.getReader());
        final ImportPayload payload = "csv".equalsIgnoreCase(format) ? parseCsv(body) : parseJson(body);
        final List<String> validationErrors = validate(payload);
        if (!validationErrors.isEmpty()) {
            beginJson(response);
            final JSONWriter json = new JSONWriter(response.getWriter());
            json.object().key("error").value("invalid-list").key("messages").array();
            for (final String validationError : validationErrors) {
                json.value(validationError);
            }
            json.endArray().endObject();
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        final ResourceResolver resolver = request.getResourceResolver();
        final Resource data = writableListResource(resolver, path);
        writePayload(data, resolver, payload);
        resolver.commit();
        writeSuccess(response, "Imported " + payload.items().size() + " item(s).", path);
    }

    private void replicate(
            final SlingHttpServletRequest request,
            final SlingHttpServletResponse response,
            final ReplicationActionType action)
            throws IOException, JSONException, ManagementException, ReplicationException {
        final String path = required(request, "path");
        requireManagedPath(path);
        final Resource resource = request.getResourceResolver().getResource(path);
        if (resource == null) {
            throw new ManagementException(SlingHttpServletResponse.SC_NOT_FOUND, "List is not readable.");
        }
        final Replicator currentReplicator = replicator;
        final Session session = request.getResourceResolver().adaptTo(Session.class);
        if (currentReplicator == null || session == null) {
            throw new ManagementException(SlingHttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "AEM replication is not available in this environment.");
        }
        currentReplicator.replicate(session, action, path);
        final String message = action == ReplicationActionType.ACTIVATE
                ? "Published Generic List."
                : "Unpublished Generic List.";
        writeSuccess(response, message, path);
    }

    private List<ListEntry> discover(
            final ResourceResolver resolver,
            final String rootPath,
            final String query,
            final int limit) {
        final Resource root = resolver.getResource(rootPath);
        if (root == null) {
            return List.of();
        }
        final List<ListEntry> entries = new ArrayList<>();
        final Set<String> paths = new LinkedHashSet<>();
        final Deque<Resource> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty() && entries.size() < limit) {
            final Resource resource = queue.removeFirst();
            final Resource data = GenericListAdapterFactory.getListResource(resource);
            if (data != null && paths.add(data.getPath())) {
                final GenericList list = new GenericListImpl(data);
                final String title = list.getTitle() == null ? data.getName() : list.getTitle();
                final String description = list.getDescription();
                if (query.isEmpty() || title.toLowerCase(Locale.ROOT).contains(query)
                        || data.getPath().toLowerCase(Locale.ROOT).contains(query)) {
                    final PublicationStatus status = publicationStatus(resolver, data.getPath());
                    entries.add(new ListEntry(data.getPath(), title, description, list.getItems().size(), list.isValid(),
                            list.getValidationIssues().size(), status.published(), status.lastPublished()));
                }
            }
            for (final Resource child : resource.getChildren()) {
                queue.addLast(child);
            }
        }
        return entries;
    }

    private List<String> findUsages(final ResourceResolver resolver, final String listPath, final int limit) {
        final List<String> usages = new ArrayList<>();
        final Set<String> visited = new HashSet<>();
        for (final String rootPath : usageSearchRoots) {
            final Resource root = resolver.getResource(rootPath);
            if (root == null) {
                continue;
            }
            final Deque<Resource> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty() && usages.size() < limit) {
                final Resource resource = queue.removeFirst();
                if (!resource.getPath().equals(listPath) && references(resource.getValueMap(), listPath)
                        && visited.add(resource.getPath())) {
                    usages.add(resource.getPath());
                }
                for (final Resource child : resource.getChildren()) {
                    queue.addLast(child);
                }
            }
        }
        return usages;
    }

    private PublicationStatus publicationStatus(final ResourceResolver resolver, final String path) {
        final Replicator currentReplicator = replicator;
        final Session session = resolver.adaptTo(Session.class);
        if (currentReplicator == null || session == null) {
            return new PublicationStatus(false, false, false, null);
        }
        final ReplicationStatus status = currentReplicator.getReplicationStatus(session, path);
        if (status == null) {
            return new PublicationStatus(true, false, false, null);
        }
        final Calendar lastPublished = status.getLastPublished();
        return new PublicationStatus(true, status.isPublished(), status.isPending(),
                lastPublished == null ? null : Instant.ofEpochMilli(lastPublished.getTimeInMillis()).toString());
    }

    private Resource writableListResource(final ResourceResolver resolver, final String path) throws ManagementException {
        final Resource resource = resolver.getResource(path);
        if (resource == null) {
            throw new ManagementException(SlingHttpServletResponse.SC_NOT_FOUND, "List is not readable.");
        }
        Resource data = GenericListAdapterFactory.getListResource(resource);
        if (data == null) {
            final PageManager pageManager = resolver.adaptTo(PageManager.class);
            final Page page = pageManager == null ? null : pageManager.getPage(path);
            data = page == null ? null : GenericListAdapterFactory.getListResource(page.getContentResource());
        }
        if (data == null || !data.isResourceType(CANONICAL_RT)) {
            throw new ManagementException(SlingHttpServletResponse.SC_BAD_REQUEST,
                    "Imports require a canonical Generic List resource or modern list page.");
        }
        return data;
    }

    private static boolean references(final ValueMap properties, final String listPath) {
        for (final Object value : properties.values()) {
            if (listPath.equals(value)) {
                return true;
            }
            if (value instanceof String[] values) {
                for (final String item : values) {
                    if (listPath.equals(item)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void writeExportJson(final JSONWriter json, final Resource data) throws JSONException {
        final ValueMap metadata = data.getValueMap();
        json.object()
                .key("title").value(metadata.get(GenericListSchema.PN_TITLE, String.class))
                .key("description").value(metadata.get(GenericListSchema.PN_DESCRIPTION, String.class))
                .key("defaultLocale").value(metadata.get(GenericListSchema.PN_DEFAULT_LOCALE, String.class))
                .key("supportedLocales").array();
        final String[] supportedLocales = metadata.get(GenericListSchema.PN_SUPPORTED_LOCALES, String[].class);
        if (supportedLocales != null) {
            for (final String locale : supportedLocales) {
                json.value(locale);
            }
        }
        json.endArray().key("items").array();
        for (final Resource item : GenericListSchema.getItemsResource(data).getChildren()) {
            final ValueMap properties = item.getValueMap();
            json.object()
                    .key("title").value(properties.get(GenericListSchema.PN_TITLE, String.class))
                    .key("value").value(properties.get(GenericListSchema.PN_VALUE, String.class))
                    .key("translations").array();
            final Resource translations = item.getChild(GenericListSchema.NN_TRANSLATIONS);
            if (translations != null) {
                for (final Resource translation : translations.getChildren()) {
                    final ValueMap translationProperties = translation.getValueMap();
                    json.object()
                            .key("locale").value(translationProperties.get(GenericListSchema.PN_LOCALE, String.class))
                            .key("title").value(translationProperties.get(GenericListSchema.PN_TRANSLATED_TITLE, String.class))
                            .endObject();
                }
            }
            json.endArray().endObject();
        }
        json.endArray().endObject();
    }

    private static String toCsv(final Resource data) {
        final StringBuilder csv = new StringBuilder("title,value,locale,localizedTitle\n");
        for (final Resource item : GenericListSchema.getItemsResource(data).getChildren()) {
            final ValueMap properties = item.getValueMap();
            final String title = properties.get(GenericListSchema.PN_TITLE, "");
            final String value = properties.get(GenericListSchema.PN_VALUE, "");
            csv.append(csv(title)).append(',').append(csv(value)).append(",,\n");
            final Resource translations = item.getChild(GenericListSchema.NN_TRANSLATIONS);
            if (translations != null) {
                for (final Resource translation : translations.getChildren()) {
                    final ValueMap translationProperties = translation.getValueMap();
                    csv.append(csv(title)).append(',').append(csv(value)).append(',')
                            .append(csv(translationProperties.get(GenericListSchema.PN_LOCALE, ""))).append(',')
                            .append(csv(translationProperties.get(GenericListSchema.PN_TRANSLATED_TITLE, ""))).append('\n');
                }
            }
        }
        return csv.toString();
    }

    private static String csv(final String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    private static ImportPayload parseJson(final String source) throws JSONException {
        final JSONObject json = new JSONObject(source);
        final List<ImportItem> items = new ArrayList<>();
        final JSONArray jsonItems = json.optJSONArray("items");
        if (jsonItems != null) {
            for (int index = 0; index < jsonItems.length(); index++) {
                final JSONObject item = jsonItems.getJSONObject(index);
                final List<ImportTranslation> translations = new ArrayList<>();
                final JSONArray jsonTranslations = item.optJSONArray("translations");
                if (jsonTranslations != null) {
                    for (int translationIndex = 0; translationIndex < jsonTranslations.length(); translationIndex++) {
                        final JSONObject translation = jsonTranslations.getJSONObject(translationIndex);
                        translations.add(new ImportTranslation(translation.optString("locale"), translation.optString("title")));
                    }
                }
                items.add(new ImportItem(item.optString("title"), item.optString("value"), translations));
            }
        }
        final List<String> supportedLocales = new ArrayList<>();
        final JSONArray locales = json.optJSONArray("supportedLocales");
        if (locales != null) {
            for (int index = 0; index < locales.length(); index++) {
                supportedLocales.add(locales.optString(index));
            }
        }
        return new ImportPayload(json.optString("title", null), json.optString("description", null),
                json.optString("defaultLocale", null), supportedLocales, items);
    }

    private static ImportPayload parseCsv(final String source) throws ManagementException {
        final List<List<String>> rows = csvRows(source);
        if (rows.isEmpty()) {
            return new ImportPayload(null, null, null, List.of(), List.of());
        }
        final Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < rows.getFirst().size(); index++) {
            columns.put(rows.getFirst().get(index).trim().toLowerCase(Locale.ROOT), index);
        }
        if (!columns.containsKey("title") || !columns.containsKey("value")) {
            throw new ManagementException(SlingHttpServletResponse.SC_BAD_REQUEST,
                    "CSV must contain title and value headers.");
        }
        final Map<String, ImportItemBuilder> items = new LinkedHashMap<>();
        for (int row = 1; row < rows.size(); row++) {
            final List<String> fields = rows.get(row);
            if (fields.stream().allMatch(String::isBlank)) {
                continue;
            }
            final String title = cell(fields, columns.get("title"));
            final String value = cell(fields, columns.get("value"));
            final ImportItemBuilder item = items.computeIfAbsent(title + "\u0000" + value,
                    ignored -> new ImportItemBuilder(title, value));
            final String locale = cell(fields, columns.get("locale"));
            final String localizedTitle = cell(fields, columns.get("localizedtitle"));
            if (!locale.isBlank() || !localizedTitle.isBlank()) {
                item.translations().add(new ImportTranslation(locale, localizedTitle));
            }
        }
        return new ImportPayload(null, null, null, List.of(),
                items.values().stream().map(ImportItemBuilder::toItem).toList());
    }

    private static List<List<String>> csvRows(final String source) throws ManagementException {
        final List<List<String>> rows = new ArrayList<>();
        final List<String> row = new ArrayList<>();
        final StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < source.length(); index++) {
            final char current = source.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < source.length() && source.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                row.add(field.toString());
                field.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && index + 1 < source.length() && source.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(field.toString());
                rows.add(new ArrayList<>(row));
                row.clear();
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new ManagementException(SlingHttpServletResponse.SC_BAD_REQUEST, "CSV contains an unclosed quoted field.");
        }
        if (!row.isEmpty() || field.length() > 0) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static String cell(final List<String> fields, final Integer index) {
        return index == null || index >= fields.size() ? "" : fields.get(index);
    }

    private static List<String> validate(final ImportPayload payload) {
        final List<String> errors = new ArrayList<>();
        if (payload.items().size() > GenericListSchema.MAX_ITEMS) {
            errors.add("A list may contain at most " + GenericListSchema.MAX_ITEMS + " items.");
        }
        if (payload.defaultLocale() != null && !payload.defaultLocale().isBlank()
                && GenericListLocale.parse(payload.defaultLocale()).isEmpty()) {
            errors.add("defaultLocale must be a BCP-47 language tag.");
        }
        final Set<String> declaredLocales = new HashSet<>();
        for (final String locale : payload.supportedLocales()) {
            final var parsed = GenericListLocale.parse(locale);
            if (parsed.isEmpty()) {
                errors.add("supportedLocales contains an invalid locale: " + locale);
            } else if (!declaredLocales.add(GenericListLocale.key(parsed.get()))) {
                errors.add("supportedLocales contains a duplicate locale: " + locale);
            }
        }
        final Set<String> values = new HashSet<>();
        int itemIndex = 0;
        for (final ImportItem item : payload.items()) {
            itemIndex++;
            if (!validText(item.title(), GenericListSchema.MAX_TITLE_LENGTH)) {
                errors.add("Item " + itemIndex + " requires a title of at most 255 characters.");
            }
            if (!validText(item.value(), GenericListSchema.MAX_VALUE_LENGTH)) {
                errors.add("Item " + itemIndex + " requires a value of at most 255 characters.");
            } else if (!values.add(item.value().trim())) {
                errors.add("Item values must be unique: " + item.value().trim());
            }
            if (item.translations().size() > GenericListSchema.MAX_TRANSLATIONS_PER_ITEM) {
                errors.add("Item " + itemIndex + " exceeds the translation limit.");
            }
            final Set<String> locales = new HashSet<>();
            for (final ImportTranslation translation : item.translations()) {
                final var parsed = GenericListLocale.parse(translation.locale());
                if (parsed.isEmpty()) {
                    errors.add("Item " + itemIndex + " has an invalid locale: " + translation.locale());
                } else if (!locales.add(GenericListLocale.key(parsed.get()))) {
                    errors.add("Item " + itemIndex + " repeats locale: " + translation.locale());
                }
                if (!validText(translation.title(), GenericListSchema.MAX_TITLE_LENGTH)) {
                    errors.add("Item " + itemIndex + " has a blank or too-long localized title.");
                }
            }
        }
        return errors;
    }

    private static boolean validText(final String value, final int limit) {
        return GenericListSchema.isNonBlank(value) && value.trim().length() <= limit;
    }

    private static void writePayload(
            final Resource data,
            final ResourceResolver resolver,
            final ImportPayload payload) throws PersistenceException {
        final ModifiableValueMap properties = data.adaptTo(ModifiableValueMap.class);
        if (properties == null) {
            throw new PersistenceException("List is not modifiable: " + data.getPath());
        }
        putOrRemove(properties, GenericListSchema.PN_TITLE, payload.title());
        putOrRemove(properties, GenericListSchema.PN_DESCRIPTION, payload.description());
        putOrRemove(properties, GenericListSchema.PN_DEFAULT_LOCALE, payload.defaultLocale());
        if (payload.supportedLocales().isEmpty()) {
            properties.remove(GenericListSchema.PN_SUPPORTED_LOCALES);
        } else {
            properties.put(GenericListSchema.PN_SUPPORTED_LOCALES, payload.supportedLocales().toArray(String[]::new));
        }
        final Resource existingItems = data.getChild(GenericListSchema.NN_ITEMS);
        if (existingItems != null) {
            resolver.delete(existingItems);
        }
        final Resource items = resolver.create(data, GenericListSchema.NN_ITEMS,
                Map.of("jcr:primaryType", "nt:unstructured"));
        int itemIndex = 0;
        for (final ImportItem item : payload.items()) {
            final Resource itemResource = resolver.create(items, "item" + itemIndex++, Map.of(
                    "jcr:primaryType", "nt:unstructured",
                    GenericListSchema.PN_TITLE, item.title().trim(),
                    GenericListSchema.PN_VALUE, item.value().trim()));
            if (!item.translations().isEmpty()) {
                final Resource translations = resolver.create(itemResource, GenericListSchema.NN_TRANSLATIONS,
                        Map.of("jcr:primaryType", "nt:unstructured"));
                int translationIndex = 0;
                for (final ImportTranslation translation : item.translations()) {
                    resolver.create(translations, "item" + translationIndex++, Map.of(
                            "jcr:primaryType", "nt:unstructured",
                            GenericListSchema.PN_LOCALE, GenericListLocale.parse(translation.locale()).orElseThrow().toLanguageTag(),
                            GenericListSchema.PN_TRANSLATED_TITLE, translation.title().trim()));
                }
            }
        }
    }

    private void requireManagedPath(final String path) throws ManagementException {
        if (path == null || !path.startsWith("/")
                || !managedRoots.stream().anyMatch(root -> path.equals(root) || path.startsWith(root + "/"))) {
            throw new ManagementException(SlingHttpServletResponse.SC_FORBIDDEN,
                    "The path is outside configured Generic List management roots.");
        }
    }

    private int boundedLimit(final String requested) {
        if (requested == null || requested.isBlank()) {
            return maxResults;
        }
        try {
            return Math.max(1, Math.min(Integer.parseInt(requested), maxResults));
        } catch (NumberFormatException ex) {
            return maxResults;
        }
    }

    private static List<String> normalizeRoots(final String[] roots, final List<String> defaults) {
        if (roots == null || roots.length == 0) {
            return defaults;
        }
        final List<String> normalized = new ArrayList<>();
        for (final String root : roots) {
            if (root != null && root.startsWith("/")) {
                normalized.add(root.endsWith("/") ? root.substring(0, root.length() - 1) : root);
            }
        }
        return normalized.isEmpty() ? defaults : List.copyOf(normalized);
    }

    private static Resource ensureFolder(final ResourceResolver resolver, final String path) throws PersistenceException {
        Resource current = resolver.getResource("/");
        if (current == null) {
            throw new PersistenceException("Repository root is unavailable.");
        }
        if ("/".equals(path)) {
            return current;
        }
        for (final String segment : path.substring(1).split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            Resource child = current.getChild(segment);
            if (child == null) {
                child = resolver.create(current, segment, Map.of("jcr:primaryType", "sling:Folder"));
            }
            current = child;
        }
        return current;
    }

    private static String parentPath(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    private static String name(final String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static void putIfNonBlank(final Map<String, Object> properties, final String name, final String value) {
        if (GenericListSchema.isNonBlank(value)) {
            properties.put(name, value.trim());
        }
    }

    private static void putOrRemove(final ModifiableValueMap properties, final String name, final String value) {
        if (GenericListSchema.isNonBlank(value)) {
            properties.put(name, value.trim());
        } else {
            properties.remove(name);
        }
    }

    private static String required(final SlingHttpServletRequest request, final String name) throws ManagementException {
        final String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            throw new ManagementException(SlingHttpServletResponse.SC_BAD_REQUEST, name + " is required.");
        }
        return value;
    }

    private static String read(final Reader reader) throws IOException {
        final StringBuilder result = new StringBuilder();
        final char[] buffer = new char[4_096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            result.append(buffer, 0, read);
        }
        return result.toString();
    }

    private static void beginJson(final SlingHttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
    }

    private static void writeSuccess(final SlingHttpServletResponse response, final String message, final String path)
            throws IOException, JSONException {
        beginJson(response);
        new JSONWriter(response.getWriter()).object().key("ok").value(true)
                .key("message").value(message).key("path").value(path).endObject();
    }

    private static void writeError(final SlingHttpServletResponse response, final int status, final String message)
            throws IOException {
        response.setStatus(status);
        beginJson(response);
        response.getWriter().write("{\"error\":" + JSONObject.quote(message) + "}");
    }

    private record ListEntry(
            String path,
            String title,
            String description,
            int items,
            boolean valid,
            int validationIssues,
            boolean published,
            String lastPublished) {
    }

    private record PublicationStatus(boolean available, boolean published, boolean pending, String lastPublished) {
    }

    private record ImportPayload(
            String title,
            String description,
            String defaultLocale,
            List<String> supportedLocales,
            List<ImportItem> items) {
    }

    private record ImportItem(String title, String value, List<ImportTranslation> translations) {
    }

    private record ImportTranslation(String locale, String title) {
    }

    private record ImportItemBuilder(String title, String value, List<ImportTranslation> translations) {
        private ImportItemBuilder(final String title, final String value) {
            this(title, value, new ArrayList<>());
        }

        private ImportItem toItem() {
            return new ImportItem(title, value, List.copyOf(translations));
        }
    }

    private static final class ManagementException extends Exception {
        private final int status;

        private ManagementException(final int status, final String message) {
            super(message);
            this.status = status;
        }

        private int status() {
            return status;
        }
    }
}
