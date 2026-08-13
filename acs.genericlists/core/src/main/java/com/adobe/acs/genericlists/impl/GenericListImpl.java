package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.GenericList;
import com.adobe.acs.genericlists.api.GenericListLocale;
import com.adobe.acs.genericlists.api.GenericListSchema;
import com.adobe.acs.genericlists.api.GenericListValidationIssue;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Internal immutable Generic List implementation. */
public class GenericListImpl implements com.adobe.acs.genericlists.api.GenericList {

    public static final String RT_GENERIC_LIST_PAGE = "acs-genericlists/components/page";
    public static final String RT_KEY_VALUE_LIST = "acs-genericlists/components/key-value-list";
    public static final String RT_LEGACY_GENERIC_LIST = "acs-genericlists/components/utilities/genericlist";
    public static final String RT_ACS_COMMONS_GENERIC_LIST = "acs-commons/components/utilities/genericlist";

    public static final class ItemImpl implements Item {

        private final String title;
        private final String value;
        private final ValueMap properties;
        private final Map<String, String> translations;

        ItemImpl(
                final String title,
                final String value,
                final ValueMap properties,
                final Map<String, String> translations) {
            this.title = title;
            this.value = value;
            this.properties = properties;
            this.translations = translations;
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
            for (final Locale candidate : GenericListLocale.fallbackChain(locale)) {
                final String key = GenericListLocale.key(candidate);
                if (key == null) {
                    continue;
                }
                final String translation = translations.get(key);
                if (GenericListSchema.isNonBlank(translation)) {
                    return translation;
                }

                // Preserve ACS/in-house dotted properties. Both legacy underscore and BCP-47 hyphen forms are
                // accepted, so old repository content does not need a write migration before it can localize.
                final String legacyUnderscore = properties.get(
                        GenericListSchema.PN_TITLE + "." + key.replace('-', '_'), String.class);
                if (GenericListSchema.isNonBlank(legacyUnderscore)) {
                    return legacyUnderscore.trim();
                }
                final String legacyBcp47 = properties.get(GenericListSchema.PN_TITLE + "." + key, String.class);
                if (GenericListSchema.isNonBlank(legacyBcp47)) {
                    return legacyBcp47.trim();
                }
            }
            return getTitle();
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    private final List<Item> items;
    private final Map<String, Item> valueMapping;
    private final List<GenericListValidationIssue> validationIssues;
    private final String title;
    private final String description;
    private final Locale defaultLocale;
    private final Set<Locale> supportedLocales;

    public GenericListImpl(final Resource listResource) {
        if (listResource == null) {
            items = List.of();
            valueMapping = Map.of();
            validationIssues = GenericListSchema.validate(null);
            title = null;
            description = null;
            defaultLocale = null;
            supportedLocales = Set.of();
            return;
        }

        validationIssues = GenericListSchema.validate(listResource);
        final ValueMap listProperties = listResource.getValueMap();
        title = trimToNull(listProperties.get(GenericListSchema.PN_TITLE, String.class));
        description = trimToNull(listProperties.get(GenericListSchema.PN_DESCRIPTION, String.class));
        defaultLocale = GenericListLocale.parse(
                listProperties.get(GenericListSchema.PN_DEFAULT_LOCALE, String.class)).orElse(null);
        supportedLocales = readSupportedLocales(listProperties);

        final List<Item> validItems = new ArrayList<>();
        final Map<String, Item> validValueMapping = new HashMap<>();
        int itemCount = 0;
        for (final Resource itemResource : GenericListSchema.getItemsResource(listResource).getChildren()) {
            itemCount++;
            if (itemCount > GenericListSchema.MAX_ITEMS) {
                break;
            }
            final ValueMap itemProperties = itemResource.getValueMap();
            final String itemTitle = trimToNull(itemProperties.get(GenericListSchema.PN_TITLE, String.class));
            final String itemValue = trimToNull(itemProperties.get(GenericListSchema.PN_VALUE, String.class));
            if (!isValidLength(itemTitle, GenericListSchema.MAX_TITLE_LENGTH)
                    || !isValidLength(itemValue, GenericListSchema.MAX_VALUE_LENGTH)
                    || validValueMapping.containsKey(itemValue)) {
                // Keep the first valid instance of a value. This makes authored order and lookup semantics agree,
                // while getValidationIssues() still tells tooling exactly why later rows were omitted.
                continue;
            }
            final Item item = new ItemImpl(itemTitle, itemValue, itemProperties, readTranslations(itemResource));
            validItems.add(item);
            validValueMapping.put(itemValue, item);
        }
        items = List.copyOf(validItems);
        valueMapping = Collections.unmodifiableMap(validValueMapping);
    }

    @Override
    public List<Item> getItems() {
        return items;
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

    @Override
    public List<GenericListValidationIssue> getValidationIssues() {
        return validationIssues;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    @Override
    public Set<Locale> getSupportedLocales() {
        return supportedLocales;
    }

    private static boolean isValidLength(final String value, final int maximum) {
        return value != null && value.length() <= maximum;
    }

    private static String trimToNull(final String value) {
        return GenericListSchema.isNonBlank(value) ? value.trim() : null;
    }

    private static Set<Locale> readSupportedLocales(final ValueMap properties) {
        final String[] values = properties.get(GenericListSchema.PN_SUPPORTED_LOCALES, String[].class);
        final String[] rawValues;
        if (values != null) {
            rawValues = values;
        } else {
            final String oneValue = properties.get(GenericListSchema.PN_SUPPORTED_LOCALES, String.class);
            rawValues = oneValue == null ? new String[0] : new String[]{oneValue};
        }
        final Set<Locale> locales = new LinkedHashSet<>();
        for (final String value : rawValues) {
            GenericListLocale.parse(value).ifPresent(locales::add);
        }
        return Collections.unmodifiableSet(locales);
    }

    private static Map<String, String> readTranslations(final Resource itemResource) {
        final Resource translations = itemResource.getChild(GenericListSchema.NN_TRANSLATIONS);
        if (translations == null) {
            return Map.of();
        }
        final Map<String, String> localizedTitles = new HashMap<>();
        int translationCount = 0;
        for (final Resource translation : translations.getChildren()) {
            translationCount++;
            if (translationCount > GenericListSchema.MAX_TRANSLATIONS_PER_ITEM) {
                break;
            }
            final ValueMap properties = translation.getValueMap();
            final String localizedTitle = trimToNull(
                    properties.get(GenericListSchema.PN_TRANSLATED_TITLE, String.class));
            final var locale = GenericListLocale.parse(properties.get(GenericListSchema.PN_LOCALE, String.class));
            if (locale.isPresent() && isValidLength(localizedTitle, GenericListSchema.MAX_TITLE_LENGTH)) {
                localizedTitles.putIfAbsent(GenericListLocale.key(locale.get()), localizedTitle);
            }
        }
        return Collections.unmodifiableMap(localizedTitles);
    }
}
