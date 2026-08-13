package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.GenericList;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exposes Generic Lists as the historic {@code /mnt/acs-commons/lists/*.json} options contract.
 *
 * <p>The provider tries canonical and legacy roots in configuration order. The default resolves new page-backed
 * lists beneath {@code /content/generic-lists} first and then original ACS lists beneath
 * {@code /etc/acs-commons/lists}, so endpoint compatibility does not require a big-bang migration.</p>
 */
@Designate(ocd = GenericListJsonResourceProvider.Config.class)
@Component(
        service = ResourceProvider.class,
        property = {
                ResourceProvider.PROPERTY_ROOT + "=" + GenericListJsonResourceProvider.ROOT,
                ResourceProvider.PROPERTY_NAME + "=acs-genericlists-json"
        })
public final class GenericListJsonResourceProvider extends ResourceProvider<Object> {

    public static final String ROOT = "/mnt/acs-commons/lists";
    public static final String DEFAULT_LIST_ROOT = "/content/generic-lists";
    public static final String LEGACY_LIST_ROOT = "/etc/acs-commons/lists";
    public static final String JSON_RESOURCE_TYPE = "acs-genericlists/components/utilities/genericlist/json";

    private static final String JSON_EXTENSION = ".json";

    @ObjectClassDefinition(
            name = "ACS Generic Lists - JSON Resource Provider",
            description = "Maps Generic List resources to JSON options below /mnt/acs-commons/lists.")
    public @interface Config {
        @AttributeDefinition(
                name = "Generic List Roots",
                description = "Roots searched in order for /mnt/acs-commons/lists requests."
                        + " The first matching readable list wins.")
        String[] list_roots() default {DEFAULT_LIST_ROOT, LEGACY_LIST_ROOT};

        /**
         * @deprecated Existing 1.2.0 configuration property. Set it only when one legacy root must override the
         *             ordered roots above.
         */
        @Deprecated
        @AttributeDefinition(name = "Legacy Generic List Root", description = "Deprecated single-root override.")
        String list_root() default "";
    }

    private List<String> listRoots = List.of(DEFAULT_LIST_ROOT, LEGACY_LIST_ROOT);

    @Activate
    void activate(final Config config) {
        final String legacyOverride = config.list_root();
        if (legacyOverride != null && !legacyOverride.isBlank()) {
            listRoots = List.of(normalizeRoot(legacyOverride));
            return;
        }
        final Set<String> roots = new LinkedHashSet<>();
        for (final String root : config.list_roots()) {
            if (root != null && !root.isBlank()) {
                roots.add(normalizeRoot(root));
            }
        }
        listRoots = roots.isEmpty() ? List.of(DEFAULT_LIST_ROOT, LEGACY_LIST_ROOT) : List.copyOf(roots);
    }

    @Override
    public Resource getResource(
            final ResolveContext<Object> resolveContext,
            final String path,
            final ResourceContext resourceContext,
            final Resource parent) {
        if (path == null || !path.equals(ROOT) && !path.startsWith(ROOT + "/") || path.contains("..")) {
            return null;
        }

        final ResourceResolver resolver = resolveContext.getResourceResolver();
        if (ROOT.equals(path)) {
            return syntheticRoot(resolver);
        }
        final String relativePath = relativeListPath(path);
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }

        for (final String listRoot : listRoots) {
            final Resource listResource = resolver.getResource(listRoot + "/" + relativePath);
            final GenericList list = GenericListJsonSupport.fromResource(listResource);
            if (list != null) {
                return jsonResource(resolver, path, list);
            }
        }
        return null;
    }

    @Override
    public Iterator<Resource> listChildren(
            final ResolveContext<Object> resolveContext,
            final Resource parent) {
        if (parent == null || !ROOT.equals(parent.getPath())) {
            return Collections.emptyIterator();
        }
        final ResourceResolver resolver = resolveContext.getResourceResolver();
        final List<Resource> children = new ArrayList<>();
        final Set<String> names = new LinkedHashSet<>();
        for (final String listRoot : listRoots) {
            final Resource root = resolver.getResource(listRoot);
            if (root == null) {
                continue;
            }
            for (final Resource child : root.getChildren()) {
                final GenericList list = GenericListJsonSupport.fromResource(child);
                if (list != null && names.add(child.getName())) {
                    children.add(jsonResource(resolver, ROOT + "/" + child.getName() + JSON_EXTENSION, list));
                }
            }
        }
        return children.iterator();
    }

    private static Resource syntheticRoot(final ResourceResolver resolver) {
        final ResourceMetadata metadata = new ResourceMetadata();
        metadata.setResolutionPath(ROOT);
        return new SyntheticResource(resolver, metadata, "sling:Folder");
    }

    private static Resource jsonResource(final ResourceResolver resolver, final String path, final GenericList list) {
        final ResourceMetadata metadata = new ResourceMetadata();
        metadata.setResolutionPath(path);
        metadata.setContentType("application/json");
        metadata.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return new JsonResource(resolver, metadata, list);
    }

    private static String relativeListPath(final String path) {
        String relativePath = path.substring(ROOT.length());
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        if (relativePath.endsWith(JSON_EXTENSION)) {
            relativePath = relativePath.substring(0, relativePath.length() - JSON_EXTENSION.length());
        }
        return relativePath;
    }

    private static String normalizeRoot(final String root) {
        String normalized = root.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static final class JsonResource extends SyntheticResource {

        private final GenericList list;

        private JsonResource(
                final ResourceResolver resourceResolver,
                final ResourceMetadata metadata,
                final GenericList list) {
            super(resourceResolver, metadata, JSON_RESOURCE_TYPE);
            this.list = list;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <AdapterType> AdapterType adaptTo(final Class<AdapterType> type) {
            if (type == InputStream.class) {
                final byte[] json = GenericListJsonSupport.toOptionsJson(list, null).getBytes(StandardCharsets.UTF_8);
                return (AdapterType) new ByteArrayInputStream(json);
            }
            if (type == GenericList.class || type == com.adobe.acs.genericlists.api.GenericList.class) {
                return (AdapterType) list;
            }
            return super.adaptTo(type);
        }
    }
}
