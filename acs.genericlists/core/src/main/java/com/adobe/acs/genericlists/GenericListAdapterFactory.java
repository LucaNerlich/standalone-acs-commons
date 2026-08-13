package com.adobe.acs.genericlists;

import com.day.cq.wcm.api.Page;
import org.apache.sling.api.adapter.AdapterFactory;
import org.osgi.service.component.annotations.Component;

/** @deprecated Compatibility OSGi adapter facade; use Resource/Page adaptation to the API interface. */
@Deprecated(since = "1.3.0", forRemoval = false)
@Component(
        service = AdapterFactory.class,
        property = {
                AdapterFactory.ADAPTABLE_CLASSES + "=com.day.cq.wcm.api.Page",
                AdapterFactory.ADAPTER_CLASSES + "=com.adobe.acs.genericlists.GenericList"
        })
public class GenericListAdapterFactory implements AdapterFactory {

    private final com.adobe.acs.genericlists.impl.GenericListAdapterFactory delegate =
            new com.adobe.acs.genericlists.impl.GenericListAdapterFactory();

    @Override
    public <AdapterType> AdapterType getAdapter(final Object adaptable, final Class<AdapterType> type) {
        return delegate.getAdapter(adaptable, type);
    }
}
