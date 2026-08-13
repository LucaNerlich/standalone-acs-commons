package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.GenericList;
import com.day.cq.wcm.api.Page;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;

import java.util.ArrayList;
import java.util.List;

/** Adapts page-backed Generic Lists while retaining canonical resource adaptation in a sibling factory. */
@Component(
        service = AdapterFactory.class,
        property = {
                AdapterFactory.ADAPTABLE_CLASSES + "=com.day.cq.wcm.api.Page",
                AdapterFactory.ADAPTER_CLASSES + "=com.adobe.acs.genericlists.GenericList",
                AdapterFactory.ADAPTER_CLASSES + "=com.adobe.acs.genericlists.api.GenericList"
        })
public final class GenericListAdapterFactory implements AdapterFactory {

    /** Optional page-content property pointing at a relative or absolute canonical list resource. */
    public static final String PN_LIST_RESOURCE_PATH = "genericListPath";

    @Override
    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType getAdapter(final Object adaptable, final Class<AdapterType> type) {
        if (!isGenericListType(type) || !(adaptable instanceof Page page)) {
            return null;
        }
        final Resource listResource = getListResource(page.getContentResource());
        return listResource == null ? null : (AdapterType) new GenericListImpl(listResource);
    }

    static boolean isGenericListType(final Class<?> type) {
        return type == GenericList.class || type == com.adobe.acs.genericlists.api.GenericList.class;
    }

    /**
     * Resolves canonical, in-house legacy, and original ACS Commons list data from a resource or page content node.
     * A configurable explicit path wins; the historical {@code root/keyValueList} location remains fast-path
     * compatible; customized templates are then supported when they contain exactly one canonical component.
     */
    static Resource getListResource(final Resource resource) {
        if (resource == null) {
            return null;
        }
        if (resource.isResourceType(GenericListImpl.RT_KEY_VALUE_LIST)) {
            return resource;
        }
        if (isLegacyResource(resource)) {
            return getLegacyItemsResource(resource);
        }
        if (!isListPage(resource)) {
            return null;
        }

        final Resource explicitResource = getExplicitListResource(resource);
        if (explicitResource != null) {
            return explicitResource;
        }

        final Resource conventionalResource = resource.getChild("root/keyValueList");
        if (conventionalResource != null && conventionalResource.isResourceType(GenericListImpl.RT_KEY_VALUE_LIST)) {
            return conventionalResource;
        }

        final List<Resource> found = new ArrayList<>(2);
        findCanonicalChildren(resource, found);
        return found.size() == 1 ? found.getFirst() : null;
    }

    private static boolean isListPage(final Resource resource) {
        return resource.isResourceType(GenericListImpl.RT_GENERIC_LIST_PAGE)
                || GenericListImpl.RT_LEGACY_GENERIC_LIST.equals(resource.getResourceType())
                || GenericListImpl.RT_ACS_COMMONS_GENERIC_LIST.equals(resource.getResourceType());
    }

    private static boolean isLegacyResource(final Resource resource) {
        return GenericListImpl.RT_LEGACY_GENERIC_LIST.equals(resource.getResourceType())
                || GenericListImpl.RT_ACS_COMMONS_GENERIC_LIST.equals(resource.getResourceType());
    }

    private static Resource getLegacyItemsResource(final Resource resource) {
        final Resource list = resource.getChild("list");
        return list == null ? null : list;
    }

    private static Resource getExplicitListResource(final Resource pageContent) {
        final ValueMap properties = pageContent.getValueMap();
        final String path = properties.get(PN_LIST_RESOURCE_PATH, String.class);
        if (path == null || path.isBlank()) {
            return null;
        }
        final Resource candidate = path.startsWith("/")
                ? pageContent.getResourceResolver().getResource(path)
                : pageContent.getChild(path);
        return candidate != null && candidate.isResourceType(GenericListImpl.RT_KEY_VALUE_LIST) ? candidate : null;
    }

    private static void findCanonicalChildren(final Resource parent, final List<Resource> found) {
        for (final Resource child : parent.getChildren()) {
            if (found.size() > 1) {
                return;
            }
            if (child.isResourceType(GenericListImpl.RT_KEY_VALUE_LIST)) {
                found.add(child);
            } else {
                findCanonicalChildren(child, found);
            }
        }
    }
}
