package com.adobe.acs.genericlists.compat;

import com.adobe.acs.commons.genericlists.GenericList;

import java.util.List;
import java.util.Locale;

/** Adapter that avoids generic return-type conflicts between the standalone and ACS Commons API packages. */
final class AcsCommonsGenericListAdapter implements GenericList {

    private final com.adobe.acs.genericlists.api.GenericList delegate;

    AcsCommonsGenericListAdapter(final com.adobe.acs.genericlists.api.GenericList delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Item> getItems() {
        return delegate.getItems().stream().map(ItemAdapter::new).map(Item.class::cast).toList();
    }

    @Override
    public String lookupTitle(final String value) {
        return delegate.lookupTitle(value);
    }

    @Override
    public String lookupTitle(final String value, final Locale locale) {
        return delegate.lookupTitle(value, locale);
    }

    private record ItemAdapter(com.adobe.acs.genericlists.api.GenericList.Item delegate) implements Item {
        @Override
        public String getTitle() {
            return delegate.getTitle();
        }

        @Override
        public String getTitle(final Locale locale) {
            return delegate.getTitle(locale);
        }

        @Override
        public String getValue() {
            return delegate.getValue();
        }
    }
}
