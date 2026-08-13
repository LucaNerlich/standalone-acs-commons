package com.adobe.acs.genericlists;

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
import java.util.Collections;
import java.util.Iterator;

@Designate(ocd = GenericListJsonResourceProvider.Config.class)
@Component(
        service = ResourceProvider.class,
        property = {
                ResourceProvider.PROPERTY_ROOT + "=" + GenericListJsonResourceProvider.ROOT,
                ResourceProvider.PROPERTY_NAME + "=acs-genericlists-json"
        })
public final class GenericListJsonResourceProvider extends ResourceProvider<Object> {

    public static final String ROOT = "/mnt/acs-commons/lists";
    static final String DEFAULT_LIST_ROOT = "/etc/acs-commons/lists";
    private static final String JSON_EXTENSION = ".json";
    private static final String JSON_RESOURCE_TYPE = "acs-genericlists/components/utilities/genericlist/json";

    @ObjectClassDefinition(
            name = "ACS Generic Lists - JSON Resource Provider",
            description = "Maps headless Generic List resources to JSON resources below /mnt/acs-commons/lists.")
    public @interface Config {
        @AttributeDefinition(name = "Generic List Root")
        String list_root() default DEFAULT_LIST_ROOT;
    }

    private String listRoot;

    @Activate
    void activate(final Config config) {
        listRoot = normalizeRoot(config.list_root());
    }

    @Override
    public Resource getResource(
            final ResolveContext<Object> resolveContext,
            final String path,
            final ResourceContext resourceContext,
            final Resource parent) {
        if (path == null || path.equals(ROOT) || !path.startsWith(ROOT + "/") || path.contains("..")) {
            return null;
        }

        String relativePath = path.substring(ROOT.length());
        if (relativePath.endsWith(JSON_EXTENSION)) {
            relativePath = relativePath.substring(0, relativePath.length() - JSON_EXTENSION.length());
        }

        final ResourceResolver resolver = resolveContext.getResourceResolver();
        final Resource listResource = resolver.getResource(listRoot + relativePath);
        final GenericList list = GenericListJsonSupport.fromResource(listResource);
        if (list == null) {
            return null;
        }

        final ResourceMetadata metadata = new ResourceMetadata();
        metadata.setResolutionPath(path);
        metadata.setContentType("application/json");
        metadata.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return new JsonResource(resolver, metadata, list);
    }

    @Override
    public Iterator<Resource> listChildren(
            final ResolveContext<Object> resolveContext,
            final Resource parent) {
        return Collections.emptyIterator();
    }

    private static String normalizeRoot(final String root) {
        if (root == null || root.isBlank()) {
            return DEFAULT_LIST_ROOT;
        }
        return root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
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
                final byte[] json = GenericListJsonSupport.toOptionsJson(list).getBytes(StandardCharsets.UTF_8);
                return (AdapterType) new ByteArrayInputStream(json);
            }
            return super.adaptTo(type);
        }
    }
}
