package com.adobe.acs.genericlists.api;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Shared, repository-safe validation for Generic List content.
 *
 * <p>Validation is deliberately usable outside the authoring dialog: imports, package installs, migration tools,
 * and custom write workflows can call it before publishing or committing content.</p>
 */
public final class GenericListSchema {

    public static final int MAX_ITEMS = 500;
    public static final int MAX_TITLE_LENGTH = 255;
    public static final int MAX_VALUE_LENGTH = 255;
    public static final int MAX_TRANSLATIONS_PER_ITEM = 50;

    public static final String PN_TITLE = "jcr:title";
    public static final String PN_DESCRIPTION = "jcr:description";
    public static final String PN_VALUE = "value";
    public static final String PN_DEFAULT_LOCALE = "defaultLocale";
    public static final String PN_SUPPORTED_LOCALES = "supportedLocales";
    public static final String NN_ITEMS = "items";
    public static final String NN_ITEM = "item";
    public static final String NN_TRANSLATIONS = "translations";
    public static final String PN_LOCALE = "locale";
    public static final String PN_TRANSLATED_TITLE = "title";

    private GenericListSchema() {
    }

    /**
     * Validates a canonical or legacy Generic List resource without mutating it.
     *
     * @param listResource the list data resource
     * @return immutable diagnostics; an empty result indicates a valid schema
     */
    public static List<GenericListValidationIssue> validate(final Resource listResource) {
        if (listResource == null) {
            return List.of(new GenericListValidationIssue("<null>", "missing-list", "List resource is missing."));
        }

        final List<GenericListValidationIssue> issues = new ArrayList<>();
        validateMetadata(listResource, issues);
        final Resource itemsResource = getItemsResource(listResource);
        final Set<String> values = new HashSet<>();
        int count = 0;
        final Iterator<Resource> children = itemsResource.listChildren();
        while (children.hasNext()) {
            final Resource item = children.next();
            count++;
            if (count > MAX_ITEMS) {
                issues.add(issue(item, "too-many-items", "A list may contain at most " + MAX_ITEMS + " items."));
                continue;
            }
            final ValueMap properties = item.getValueMap();
            final String title = properties.get(PN_TITLE, String.class);
            final String value = properties.get(PN_VALUE, String.class);
            validateNonBlank(item, PN_TITLE, title, MAX_TITLE_LENGTH, issues);
            validateNonBlank(item, PN_VALUE, value, MAX_VALUE_LENGTH, issues);
            if (isNonBlank(value) && !values.add(value.trim())) {
                issues.add(issue(item, "duplicate-value", "Item values must be unique; '" + value.trim() + "' is repeated."));
            }
            validateTranslations(item, issues);
        }
        return List.copyOf(issues);
    }

    /**
     * Returns the node that contains ordered row children for canonical, in-house legacy, and ACS legacy shapes.
     */
    public static Resource getItemsResource(final Resource listResource) {
        final Resource items = listResource.getChild(NN_ITEMS);
        if (items != null) {
            return items;
        }
        final Resource legacyItems = listResource.getChild(NN_ITEM);
        return legacyItems == null ? listResource : legacyItems;
    }

    public static boolean isNonBlank(final String value) {
        return value != null && !value.isBlank();
    }

    private static void validateMetadata(final Resource resource, final List<GenericListValidationIssue> issues) {
        final ValueMap properties = resource.getValueMap();
        final String defaultLocale = properties.get(PN_DEFAULT_LOCALE, String.class);
        if (defaultLocale != null && GenericListLocale.parse(defaultLocale).isEmpty()) {
            issues.add(issue(resource, "invalid-default-locale", "defaultLocale must be a BCP-47 language tag."));
        }
        String[] supportedLocales = properties.get(PN_SUPPORTED_LOCALES, String[].class);
        if (supportedLocales == null) {
            final String supportedLocale = properties.get(PN_SUPPORTED_LOCALES, String.class);
            supportedLocales = supportedLocale == null ? new String[0] : new String[]{supportedLocale};
        }
        final Set<String> locales = new HashSet<>();
        for (final String supportedLocale : supportedLocales) {
            final var locale = GenericListLocale.parse(supportedLocale);
            if (locale.isEmpty()) {
                issues.add(issue(resource, "invalid-supported-locale", "supportedLocales must contain BCP-47 language tags."));
            } else if (!locales.add(GenericListLocale.key(locale.get()))) {
                issues.add(issue(resource, "duplicate-supported-locale", "supportedLocales contains a duplicate locale."));
            }
        }
    }

    private static void validateTranslations(final Resource item, final List<GenericListValidationIssue> issues) {
        final Resource translations = item.getChild(NN_TRANSLATIONS);
        if (translations == null) {
            return;
        }
        int count = 0;
        final Set<String> locales = new HashSet<>();
        for (final Resource translation : translations.getChildren()) {
            count++;
            if (count > MAX_TRANSLATIONS_PER_ITEM) {
                issues.add(issue(translation, "too-many-translations",
                        "An item may contain at most " + MAX_TRANSLATIONS_PER_ITEM + " localized titles."));
                continue;
            }
            final ValueMap properties = translation.getValueMap();
            final String locale = properties.get(PN_LOCALE, String.class);
            final String title = properties.get(PN_TRANSLATED_TITLE, String.class);
            if (GenericListLocale.parse(locale).isEmpty()) {
                issues.add(issue(translation, "invalid-locale", "Locale must be a BCP-47 language tag."));
            } else if (!locales.add(GenericListLocale.key(GenericListLocale.parse(locale).orElseThrow()))) {
                issues.add(issue(translation, "duplicate-locale", "Each item may define a locale only once."));
            }
            validateNonBlank(translation, PN_TRANSLATED_TITLE, title, MAX_TITLE_LENGTH, issues);
        }
    }

    private static void validateNonBlank(
            final Resource resource,
            final String property,
            final String value,
            final int maxLength,
            final List<GenericListValidationIssue> issues) {
        if (!isNonBlank(value)) {
            issues.add(issue(resource, "blank-" + property.replace(':', '-'), property + " must be nonblank."));
        } else if (value.trim().length() > maxLength) {
            issues.add(issue(resource, "too-long-" + property.replace(':', '-'),
                    property + " may contain at most " + maxLength + " characters."));
        }
    }

    private static GenericListValidationIssue issue(final Resource resource, final String code, final String message) {
        return new GenericListValidationIssue(resource.getPath(), code, message);
    }
}
