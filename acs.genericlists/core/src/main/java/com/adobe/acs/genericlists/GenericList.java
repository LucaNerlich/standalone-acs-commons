package com.adobe.acs.genericlists;

import org.osgi.annotation.versioning.ProviderType;

import java.util.List;
import java.util.Locale;

/**
 * Legacy standalone Generic List API retained for binary/source compatibility.
 *
 * @deprecated Since 1.3.0, new integrations should consume
 *             {@link com.adobe.acs.genericlists.api.GenericList}. This contract remains unchanged so existing
 *             consumers and nested {@link Item} references continue to work.
 */
@Deprecated(since = "1.3.0", forRemoval = false)
@ProviderType
public interface GenericList {

    /** @return ordered title/value items */
    List<Item> getItems();

    /** @return title for a value, or {@code null} */
    String lookupTitle(String value);

    /** @return localized title for a value, or {@code null} */
    String lookupTitle(String value, Locale locale);

    /** A title/value pair. */
    @ProviderType
    interface Item {
        String getTitle();

        String getTitle(Locale locale);

        String getValue();
    }
}
