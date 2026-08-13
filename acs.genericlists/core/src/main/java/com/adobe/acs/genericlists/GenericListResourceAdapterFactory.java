package com.adobe.acs.genericlists;

import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

@Component(
        service = AdapterFactory.class,
        property = {
                AdapterFactory.ADAPTABLE_CLASSES + "=org.apache.sling.api.resource.Resource",
                AdapterFactory.ADAPTER_CLASSES + "=com.adobe.acs.genericlists.GenericList"
        })
public class GenericListResourceAdapterFactory implements AdapterFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType getAdapter(final Object adaptable, final Class<AdapterType> type) {
        if (type != GenericList.class || !(adaptable instanceof Resource resource)) {
            return null;
        }

        final Resource listResource = GenericListAdapterFactory.getListResource(resource);
        return listResource == null ? null : (AdapterType) new GenericListImpl(listResource);
    }
}
