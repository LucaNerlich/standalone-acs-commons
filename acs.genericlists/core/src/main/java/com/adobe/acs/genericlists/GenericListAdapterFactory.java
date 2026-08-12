package com.adobe.acs.genericlists;

import com.day.cq.wcm.api.Page;
import org.apache.sling.api.adapter.AdapterFactory;
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

        if (page.getContentResource() == null
                || !page.getContentResource().isResourceType(GenericListImpl.RT_GENERIC_LIST)) {
            return null;
        }

        return (AdapterType) new GenericListImpl(page.getContentResource().getChild("list"));
    }
}
