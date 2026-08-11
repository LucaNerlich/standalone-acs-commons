package com.adobe.acs.genericlists;

import java.util.List;
import java.util.Locale;

/**
 * A generic, author-managed list of title/value pairs (e.g. to back a Granite UI select's options).
 * <p>
 * Adapt a {@link com.day.cq.wcm.api.Page} whose content resource is
 * {@code acs-genericlists/components/utilities/genericlist} to this type via {@link GenericListAdapterFactory}.
 * There is no Sling Model registered directly against that resource type - construct {@link GenericListImpl}
 * yourself (from any resource's {@code list} child) if you need to read one without a {@code Page}.
 */
public interface GenericList {

    /**
     * @return an ordered list of title/value pairs
     */
    List<Item> getItems();

    /**
     * @param value the list item's value
     * @return the title for the given value, or {@code null} if not found
     */
    String lookupTitle(String value);

    /**
     * @param value  the list item's value
     * @param locale the locale to localize the title for
     * @return the localized title for the given value, or {@code null} if not found
     */
    String lookupTitle(String value, Locale locale);

    /**
     * A single title/value pair within a {@link GenericList}.
     */
    interface Item {

        /**
         * @return the item's title
         */
        String getTitle();

        /**
         * @param locale the locale to localize the title for
         * @return the item's title, localized for the given locale if a {@code jcr:title.<locale>} property
         * exists, falling back to {@link #getTitle()} otherwise
         */
        String getTitle(Locale locale);

        /**
         * @return the item's value
         */
        String getValue();
    }
}
