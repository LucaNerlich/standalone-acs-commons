package com.adobe.acs.genericlists;

import com.day.cq.wcm.api.Page;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

@Component(
        service = AdapterFactory.class,
        property = {
                AdapterFactory.ADAPTABLE_CLASSES + "=com.day.cq.wcm.api.Page",
                AdapterFactory.ADAPTER_CLASSES + "=com.adobe.acs.genericlists.GenericList"
        })
public class GenericListAdapterFactory implements AdapterFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType getAdapter(final Object adaptable, final Class<AdapterType> type) {
        if (type != GenericList.class || !(adaptable instanceof Page page)) {
            return null;
        }

        final Resource pageContent = page.getContentResource();
        if (pageContent == null) {
            return null;
        }

        final Resource listResource = getListResource(pageContent);
        if (listResource == null) {
            return null;
        }

        return (AdapterType) new GenericListImpl(listResource);
    }

    private static Resource getListResource(final Resource pageContent) {
        if (pageContent.isResourceType(GenericListImpl.RT_GENERIC_LIST_PAGE)) {
            final Resource component = pageContent.getChild("root/keyValueList");
            return component != null && component.isResourceType(GenericListImpl.RT_KEY_VALUE_LIST)
                    ? component
                    : null;
        }

        // Preserve adaptation of pages created with versions <= 1.1.0.
        return pageContent.isResourceType(GenericListImpl.RT_LEGACY_GENERIC_LIST)
                ? pageContent.getChild("list")
                : null;
    }
}
