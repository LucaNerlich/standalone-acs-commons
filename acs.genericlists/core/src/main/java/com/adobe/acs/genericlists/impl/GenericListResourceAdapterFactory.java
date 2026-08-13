package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.GenericList;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

/** Adapts canonical resources, compatibility resource types, and page content resources to Generic Lists. */
@Component(
        service = AdapterFactory.class,
        property = {
                AdapterFactory.ADAPTABLE_CLASSES + "=org.apache.sling.api.resource.Resource",
                AdapterFactory.ADAPTER_CLASSES + "=com.adobe.acs.genericlists.GenericList",
                AdapterFactory.ADAPTER_CLASSES + "=com.adobe.acs.genericlists.api.GenericList"
        })
public final class GenericListResourceAdapterFactory implements AdapterFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType getAdapter(final Object adaptable, final Class<AdapterType> type) {
        if (!GenericListAdapterFactory.isGenericListType(type) || !(adaptable instanceof Resource resource)) {
            return null;
        }
        final Resource listResource = GenericListAdapterFactory.getListResource(resource);
        return listResource == null ? null : (AdapterType) new GenericListImpl(listResource);
    }
}
