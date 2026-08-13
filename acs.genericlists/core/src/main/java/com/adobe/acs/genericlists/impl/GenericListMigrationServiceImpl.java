package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.api.GenericList;
import com.adobe.acs.genericlists.api.GenericListLocale;
import com.adobe.acs.genericlists.api.GenericListMigrationReport;
import com.adobe.acs.genericlists.api.GenericListMigrationService;
import com.adobe.acs.genericlists.api.GenericListSchema;
import com.adobe.acs.genericlists.api.GenericListValidationIssue;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Default migration implementation. Repository writes always use the caller's resolver and permissions. */
@Component(service = GenericListMigrationService.class)
public final class GenericListMigrationServiceImpl implements GenericListMigrationService {

    private static final String DEFAULT_TEMPLATE = "/conf/acs-genericlists/settings/wcm/templates/generic-list";
    private static final String RT_CONTAINER = "core/wcm/components/container/v1/container";

    @Override
    public GenericListMigrationReport migrate(
            final Resource source,
            final String targetPath,
            final boolean overwrite,
            final boolean dryRun) {
        final List<String> messages = new ArrayList<>();
        if (source == null) {
            return report(null, targetPath, dryRun, false, List.of("Source resource is required."), List.of());
        }
        if (targetPath == null || targetPath.isBlank() || !targetPath.startsWith("/")) {
            return report(source.getPath(), targetPath, dryRun, false,
                    List.of("targetPath must be an absolute repository path."), List.of());
        }

        final Resource dataResource = resolveDataResource(source);
        if (dataResource == null) {
            return report(source.getPath(), targetPath, dryRun, false,
                    List.of("Source is not a supported Generic List or legacy list page."), List.of());
        }
        final GenericList list = new GenericListImpl(dataResource);
        final List<GenericListValidationIssue> validationIssues = list.getValidationIssues();
        final Page sourcePage = resolvePage(source);
        final boolean inPlacePageMigration = sourcePage != null && targetPath.equals(sourcePage.getPath());
        final int validRows = list.getItems().size();

        messages.add("Found " + validRows + " valid item(s) in " + dataResource.getPath() + ".");
        if (!validationIssues.isEmpty()) {
            messages.add("Skipped malformed rows; see validationIssues for details.");
        }
        if (inPlacePageMigration) {
            messages.add("Will upgrade the page resource type and create root/keyValueList while preserving legacy rows.");
        } else {
            messages.add("Will create canonical list resource at " + targetPath + ".");
        }
        if (dryRun) {
            return report(source.getPath(), targetPath, true, false, messages, validationIssues);
        }

        final ResourceResolver resolver = source.getResourceResolver();
        try {
            final Resource target = inPlacePageMigration
                    ? ensurePageListComponent(sourcePage, overwrite)
                    : ensureStandaloneTarget(resolver, targetPath, overwrite);
            copyListMetadata(dataResource, target);
            copyItems(dataResource, target, resolver);
            resolver.commit();
            messages.add("Migration completed.");
            return report(source.getPath(), targetPath, false, true, messages, validationIssues);
        } catch (PersistenceException | RuntimeException ex) {
            // The primary exception is more useful to an operator; a subsequent retry is safe because writes are
            // committed only after the full target has been prepared.
            resolver.revert();
            messages.add("Migration failed: " + ex.getMessage());
            return report(source.getPath(), targetPath, false, false, messages, validationIssues);
        }
    }

    private static Resource resolveDataResource(final Resource source) {
        Resource data = GenericListAdapterFactory.getListResource(source);
        if (data != null) {
            return data;
        }
        final Page page = resolvePage(source);
        return page == null ? null : GenericListAdapterFactory.getListResource(page.getContentResource());
    }

    private static Page resolvePage(final Resource source) {
        final PageManager pageManager = source.getResourceResolver().adaptTo(PageManager.class);
        if (pageManager == null) {
            return null;
        }
        Page page = pageManager.getPage(source.getPath());
        return page == null ? pageManager.getContainingPage(source) : page;
    }

    private static Resource ensurePageListComponent(final Page page, final boolean overwrite) throws PersistenceException {
        final ResourceResolver resolver = page.getContentResource().getResourceResolver();
        final Resource pageContent = page.getContentResource();
        final ModifiableValueMap pageProperties = pageContent.adaptTo(ModifiableValueMap.class);
        if (pageProperties == null) {
            throw new PersistenceException("Page content is not modifiable: " + pageContent.getPath());
        }
        pageProperties.put("sling:resourceType", GenericListImpl.RT_GENERIC_LIST_PAGE);
        pageProperties.put("cq:template", DEFAULT_TEMPLATE);

        Resource root = pageContent.getChild("root");
        if (root == null) {
            root = resolver.create(pageContent, "root", Map.of(
                    "jcr:primaryType", "nt:unstructured",
                    "sling:resourceType", RT_CONTAINER,
                    "layout", "responsiveGrid"));
        }
        Resource component = root.getChild("keyValueList");
        if (component != null && !overwrite) {
            throw new PersistenceException("Target page already contains root/keyValueList; set overwrite=true to replace it.");
        }
        if (component != null) {
            resolver.delete(component);
        }
        return resolver.create(root, "keyValueList", Map.of(
                "jcr:primaryType", "nt:unstructured",
                "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST));
    }

    private static Resource ensureStandaloneTarget(
            final ResourceResolver resolver,
            final String targetPath,
            final boolean overwrite) throws PersistenceException {
        Resource existing = resolver.getResource(targetPath);
        if (existing != null) {
            if (!overwrite) {
                throw new PersistenceException("Target already exists; set overwrite=true to replace it: " + targetPath);
            }
            resolver.delete(existing);
        }
        final int slash = targetPath.lastIndexOf('/');
        final Resource parent = ensureFolder(resolver, targetPath.substring(0, slash));
        return resolver.create(parent, targetPath.substring(slash + 1), Map.of(
                "jcr:primaryType", "nt:unstructured",
                "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST));
    }

    private static Resource ensureFolder(final ResourceResolver resolver, final String path) throws PersistenceException {
        if (path.isBlank() || "/".equals(path)) {
            final Resource root = resolver.getResource("/");
            if (root == null) {
                throw new PersistenceException("Repository root is unavailable.");
            }
            return root;
        }
        Resource current = resolver.getResource("/");
        for (final String segment : path.substring(1).split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            Resource next = current.getChild(segment);
            if (next == null) {
                next = resolver.create(current, segment, Map.of("jcr:primaryType", "sling:Folder"));
            }
            current = next;
        }
        return current;
    }

    private static void copyListMetadata(final Resource source, final Resource target) throws PersistenceException {
        final ModifiableValueMap targetProperties = target.adaptTo(ModifiableValueMap.class);
        if (targetProperties == null) {
            throw new PersistenceException("Target is not modifiable: " + target.getPath());
        }
        final ValueMap sourceProperties = source.getValueMap();
        copyProperty(sourceProperties, targetProperties, GenericListSchema.PN_TITLE);
        copyProperty(sourceProperties, targetProperties, GenericListSchema.PN_DESCRIPTION);
        copyProperty(sourceProperties, targetProperties, GenericListSchema.PN_DEFAULT_LOCALE);
        copyProperty(sourceProperties, targetProperties, GenericListSchema.PN_SUPPORTED_LOCALES);
    }

    private static void copyProperty(final ValueMap source, final ModifiableValueMap target, final String name) {
        final Object value = source.get(name);
        if (value != null) {
            target.put(name, value);
        }
    }

    private static void copyItems(
            final Resource source,
            final Resource target,
            final ResourceResolver resolver) throws PersistenceException {
        final Resource existingItems = target.getChild(GenericListSchema.NN_ITEMS);
        if (existingItems != null) {
            resolver.delete(existingItems);
        }
        final Resource targetItems = resolver.create(target, GenericListSchema.NN_ITEMS,
                Map.of("jcr:primaryType", "nt:unstructured"));
        final Set<String> values = new HashSet<>();
        int index = 0;
        for (final Resource sourceItem : GenericListSchema.getItemsResource(source).getChildren()) {
            if (index >= GenericListSchema.MAX_ITEMS) {
                break;
            }
            final ValueMap properties = sourceItem.getValueMap();
            final String title = trim(properties.get(GenericListSchema.PN_TITLE, String.class));
            final String value = trim(properties.get(GenericListSchema.PN_VALUE, String.class));
            if (title == null || value == null || title.length() > GenericListSchema.MAX_TITLE_LENGTH
                    || value.length() > GenericListSchema.MAX_VALUE_LENGTH || !values.add(value)) {
                continue;
            }
            final Resource targetItem = resolver.create(targetItems, "item" + index++, Map.of(
                    "jcr:primaryType", "nt:unstructured",
                    GenericListSchema.PN_TITLE, title,
                    GenericListSchema.PN_VALUE, value));
            copyTranslations(sourceItem, targetItem, resolver);
        }
    }

    private static void copyTranslations(
            final Resource sourceItem,
            final Resource targetItem,
            final ResourceResolver resolver) throws PersistenceException {
        final Map<String, String> translations = new LinkedHashMap<>();
        final Resource translationRows = sourceItem.getChild(GenericListSchema.NN_TRANSLATIONS);
        if (translationRows != null) {
            for (final Resource translation : translationRows.getChildren()) {
                final ValueMap properties = translation.getValueMap();
                final String title = trim(properties.get(GenericListSchema.PN_TRANSLATED_TITLE, String.class));
                GenericListLocale.parse(properties.get(GenericListSchema.PN_LOCALE, String.class))
                        .ifPresent(locale -> {
                            if (title != null && title.length() <= GenericListSchema.MAX_TITLE_LENGTH) {
                                translations.putIfAbsent(GenericListLocale.key(locale), title);
                            }
                        });
            }
        }
        for (final Map.Entry<String, Object> entry : sourceItem.getValueMap().entrySet()) {
            if (!entry.getKey().startsWith(GenericListSchema.PN_TITLE + ".")
                    || !(entry.getValue() instanceof String value)) {
                continue;
            }
            final String title = trim(value);
            GenericListLocale.parse(entry.getKey().substring((GenericListSchema.PN_TITLE + ".").length()))
                    .ifPresent(locale -> {
                        if (title != null && title.length() <= GenericListSchema.MAX_TITLE_LENGTH) {
                            translations.putIfAbsent(GenericListLocale.key(locale), title);
                        }
                    });
        }
        if (translations.isEmpty()) {
            return;
        }
        final Resource targetTranslations = resolver.create(targetItem, GenericListSchema.NN_TRANSLATIONS,
                Map.of("jcr:primaryType", "nt:unstructured"));
        int index = 0;
        for (final Map.Entry<String, String> translation : translations.entrySet()) {
            if (index >= GenericListSchema.MAX_TRANSLATIONS_PER_ITEM) {
                break;
            }
            resolver.create(targetTranslations, "item" + index++, Map.of(
                    "jcr:primaryType", "nt:unstructured",
                    GenericListSchema.PN_LOCALE, translation.getKey(),
                    GenericListSchema.PN_TRANSLATED_TITLE, translation.getValue()));
        }
    }

    private static String trim(final String value) {
        return GenericListSchema.isNonBlank(value) ? value.trim() : null;
    }

    private static GenericListMigrationReport report(
            final String sourcePath,
            final String targetPath,
            final boolean dryRun,
            final boolean migrated,
            final List<String> messages,
            final List<GenericListValidationIssue> validationIssues) {
        return new GenericListMigrationReport(sourcePath, targetPath, dryRun, migrated, messages, validationIssues);
    }
}
