package com.adobe.acs.genericlists;

import org.apache.sling.api.adapter.AdapterFactory;
import org.osgi.service.component.annotations.Component;

/** @deprecated Compatibility OSGi resource-adapter facade. */
@Deprecated(since = "1.3.0", forRemoval = false)
@Component(
        service = AdapterFactory.class,
        property = {
                AdapterFactory.ADAPTABLE_CLASSES + "=org.apache.sling.api.resource.Resource",
                AdapterFactory.ADAPTER_CLASSES + "=com.adobe.acs.genericlists.GenericList"
        })
public class GenericListResourceAdapterFactory implements AdapterFactory {

    private final com.adobe.acs.genericlists.impl.GenericListResourceAdapterFactory delegate =
            new com.adobe.acs.genericlists.impl.GenericListResourceAdapterFactory();

    @Override
    public <AdapterType> AdapterType getAdapter(final Object adaptable, final Class<AdapterType> type) {
        return delegate.getAdapter(adaptable, type);
    }
}
