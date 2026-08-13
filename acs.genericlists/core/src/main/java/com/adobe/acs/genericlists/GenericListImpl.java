package com.adobe.acs.genericlists;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.Self;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Model(
        adaptables = Resource.class,
        adapters = GenericList.class,
        resourceType = GenericListImpl.RT_KEY_VALUE_LIST)
public final class GenericListImpl implements GenericList {

    static final String RT_GENERIC_LIST_PAGE = "acs-genericlists/components/page";
    static final String RT_KEY_VALUE_LIST = "acs-genericlists/components/key-value-list";
    static final String RT_LEGACY_GENERIC_LIST = "acs-genericlists/components/utilities/genericlist";
    static final String PN_TITLE = "jcr:title";
    static final String PN_VALUE = "value";
    static final String TITLE_PREFIX = PN_TITLE + ".";
    static final String NN_ITEMS = "items";
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

    @Inject
    public GenericListImpl(@Self final Resource listResource) {
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
                    final ItemImpl item = new ItemImpl(title, value, map);
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
