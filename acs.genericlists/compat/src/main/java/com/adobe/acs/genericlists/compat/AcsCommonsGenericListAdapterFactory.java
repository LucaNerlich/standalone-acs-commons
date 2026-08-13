package com.adobe.acs.genericlists.compat;

import com.adobe.acs.commons.genericlists.GenericList;
import com.day.cq.wcm.api.Page;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

/** Bridges Resource/Page adaptation to the optional ACS Commons API package. */
@Component(
        service = AdapterFactory.class,
        property = {
                AdapterFactory.ADAPTABLE_CLASSES + "=org.apache.sling.api.resource.Resource",
                AdapterFactory.ADAPTABLE_CLASSES + "=com.day.cq.wcm.api.Page",
                AdapterFactory.ADAPTER_CLASSES + "=com.adobe.acs.commons.genericlists.GenericList"
        })
public final class AcsCommonsGenericListAdapterFactory implements AdapterFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType getAdapter(final Object adaptable, final Class<AdapterType> type) {
        if (type != GenericList.class) {
            return null;
        }
        final com.adobe.acs.genericlists.api.GenericList delegate;
        if (adaptable instanceof Resource resource) {
            delegate = resource.adaptTo(com.adobe.acs.genericlists.api.GenericList.class);
        } else if (adaptable instanceof Page page) {
            delegate = page.adaptTo(com.adobe.acs.genericlists.api.GenericList.class);
        } else {
            return null;
        }
        return delegate == null ? null : (AdapterType) new AcsCommonsGenericListAdapter(delegate);
    }
}
