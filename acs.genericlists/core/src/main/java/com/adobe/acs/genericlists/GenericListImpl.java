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

    static final String RT_GENERIC_LIST = "acs-genericlists/components/utilities/genericlist";
    static final String PN_TITLE = "jcr:title";
    static final String PN_VALUE = "value";
    static final String TITLE_PREFIX = PN_TITLE + ".";
    static final String NN_ITEM = "item";

    public static final class ItemImpl implements Item {

        private final String title;
        private final String value;
        private final ValueMap props;

        public ItemImpl(final String title, final String value, final ValueMap props) {
            this.title = title;
            this.value = value;
            this.props = props;
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
            return props.get(TITLE_PREFIX + locale.toString().toLowerCase(Locale.ROOT), String.class);
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    private final List<Item> items;
    private final Map<String, Item> valueMapping;

    public GenericListImpl(final Resource listParsys) {
        if (listParsys == null) {
            items = Collections.emptyList();
            valueMapping = Collections.emptyMap();
        } else {
            final List<Item> tempItems = new ArrayList<>();
            final Map<String, Item> tempValueMapping = new HashMap<>();
            // The Granite multifield dialog nests rows one level deeper, under a fixed "item" child (matching
            // the "name" path given to its composite field) - fall back to the parsys' own direct children for
            // content authored without going through that dialog (e.g. via repoinit).
            final Resource itemsNode = listParsys.getChild(NN_ITEM) != null ? listParsys.getChild(NN_ITEM) : listParsys;
            final Iterator<Resource> children = itemsNode.listChildren();
            while (children.hasNext()) {
                final Resource res = children.next();
                final ValueMap map = res.getValueMap();
                final String title = map.get(PN_TITLE, String.class);
                final String value = map.get(PN_VALUE, String.class);
                if (title != null) {
                    final ItemImpl item = new ItemImpl(title, value, map);
                    tempItems.add(item);
                    tempValueMapping.put(value, item);
                }
            }
            items = Collections.unmodifiableList(tempItems);
            valueMapping = Collections.unmodifiableMap(tempValueMapping);
        }
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
