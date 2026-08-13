package com.adobe.acs.genericlists;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GenericListImpl implements GenericList {

    static final String RT_GENERIC_LIST_PAGE = "acs-genericlists/components/page";
    static final String RT_KEY_VALUE_LIST = "acs-genericlists/components/key-value-list";
    static final String RT_LEGACY_GENERIC_LIST = "acs-genericlists/components/utilities/genericlist";
    static final String PN_TITLE = "jcr:title";
    static final String PN_VALUE = "value";
    static final String TITLE_PREFIX = PN_TITLE + ".";
    static final String NN_ITEMS = "items";
    static final String NN_ITEM = "item";
    static final String NN_TRANSLATIONS = "translations";
    static final String PN_LOCALE = "locale";
    static final String PN_TRANSLATED_TITLE = "title";

    public static final class ItemImpl implements Item {

        private final String title;
        private final String value;
        private final ValueMap props;
        private final Resource itemResource;

        public ItemImpl(final String title, final String value, final ValueMap props) {
            this(title, value, props, null);
        }

        public ItemImpl(final String title, final String value, final ValueMap props, final Resource itemResource) {
            this.title = title;
            this.value = value;
            this.props = props;
            this.itemResource = itemResource;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getTitle(final Locale locale) {
            if (locale == null) {
                return getTitle();
            }

            final String language = locale.getLanguage();
            if (language.isEmpty()) {
                return getTitle();
            }

            String localizedTitle = null;
            if (!locale.getCountry().isEmpty()) {
                localizedTitle = getLocalizedTitle(locale);
            }
            if (localizedTitle == null) {
                localizedTitle = getLocalizedTitle(Locale.of(language));
            }
            return localizedTitle == null ? getTitle() : localizedTitle;
        }

        private String getLocalizedTitle(final Locale locale) {
            final String localeKey = locale.toString().toLowerCase(Locale.ROOT);
            final String propertyTitle = props.get(TITLE_PREFIX + localeKey, String.class);
            if (propertyTitle != null) {
                return propertyTitle;
            }

            final Resource translations = itemResource == null ? null : itemResource.getChild(NN_TRANSLATIONS);
            if (translations == null) {
                return null;
            }
            for (final Resource translation : translations.getChildren()) {
                final ValueMap translationProperties = translation.getValueMap();
                final String authoredLocale = translationProperties.get(PN_LOCALE, String.class);
                if (authoredLocale != null
                        && localeKey.equals(authoredLocale.trim().replace('-', '_').toLowerCase(Locale.ROOT))) {
                    return translationProperties.get(PN_TRANSLATED_TITLE, String.class);
                }
            }
            return null;
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    private final List<Item> items;
    private final Map<String, Item> valueMapping;

    public GenericListImpl(final Resource listResource) {
        if (listResource == null) {
            items = Collections.emptyList();
            valueMapping = Collections.emptyMap();
        } else {
            final List<Item> tempItems = new ArrayList<>();
            final Map<String, Item> tempValueMapping = new HashMap<>();
            // Composite multifields persist their rows below the field's named child. The legacy page-properties
            // dialog used "item" while the standalone component uses the clearer "items" name.
            final Resource itemsNode = getItemsResource(listResource);
            final Iterator<Resource> children = itemsNode.listChildren();
            while (children.hasNext()) {
                final Resource res = children.next();
                final ValueMap map = res.getValueMap();
                final String title = map.get(PN_TITLE, String.class);
                final String value = map.get(PN_VALUE, String.class);
                if (title != null) {
                    final ItemImpl item = new ItemImpl(title, value, map, res);
                    tempItems.add(item);
                    tempValueMapping.put(value, item);
                }
            }
            items = Collections.unmodifiableList(tempItems);
            valueMapping = Collections.unmodifiableMap(tempValueMapping);
        }
    }

    private static Resource getItemsResource(final Resource listResource) {
        final Resource items = listResource.getChild(NN_ITEMS);
        if (items != null) {
            return items;
        }
        final Resource legacyItems = listResource.getChild(NN_ITEM);
        return legacyItems == null ? listResource : legacyItems;
    }

    @Override
    public List<Item> getItems() {
        return new ArrayList<>(items);
    }

    @Override
    public String lookupTitle(final String value) {
        final Item item = valueMapping.get(value);
        return item == null ? null : item.getTitle();
    }

    @Override
    public String lookupTitle(final String value, final Locale locale) {
        final Item item = valueMapping.get(value);
        return item == null ? null : item.getTitle(locale);
    }
}
