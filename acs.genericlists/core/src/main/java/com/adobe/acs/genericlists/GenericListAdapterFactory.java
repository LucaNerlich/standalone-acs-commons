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

        final Resource listResource = getListResource(page.getContentResource());
        if (listResource == null) {
            return null;
        }

        return (AdapterType) new GenericListImpl(listResource);
    }

    static Resource getListResource(final Resource pageContent) {
        if (pageContent == null) {
            return null;
        }
        if (GenericListImpl.RT_KEY_VALUE_LIST.equals(pageContent.getResourceType())) {
            return pageContent;
        }

        // Check the exact in-house legacy type before isResourceType(), which follows the supertype chain.
        if (GenericListImpl.RT_LEGACY_GENERIC_LIST.equals(pageContent.getResourceType())) {
            return pageContent.getChild("list");
        }

        if (pageContent.isResourceType(GenericListImpl.RT_GENERIC_LIST_PAGE)) {
            final Resource component = pageContent.getChild("root/keyValueList");
            return component != null && component.isResourceType(GenericListImpl.RT_KEY_VALUE_LIST)
                    ? component
                    : null;
        }

        return null;
    }
}
