package com.adobe.acs.commons.genericlists;

import org.osgi.annotation.versioning.ProviderType;

import java.util.List;
import java.util.Locale;

/**
 * Optional compatibility copy of the ACS Commons GenericList API.
 *
 * <p>Install the compatibility bridge only after removing the ACS Commons bundle that exports this package. It is
 * intended to let existing application source/binaries move to the standalone implementation during a controlled
 * migration window.</p>
 */
@ProviderType
public interface GenericList {

    List<Item> getItems();

    String lookupTitle(String value);

    String lookupTitle(String value, Locale locale);

    @ProviderType
    interface Item {
        String getTitle();

        String getTitle(Locale locale);

        String getValue();
    }
}
