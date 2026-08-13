package com.adobe.acs.genericlists.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * BCP-47 parsing, normalization, and fallback helpers shared by Generic List readers and delivery endpoints.
 */
public final class GenericListLocale {

    private GenericListLocale() {
    }

    /**
     * Parses a BCP-47 language tag. Legacy underscore separators are accepted and normalized.
     *
     * @param value a locale tag such as {@code de-CH}
     * @return a locale only when the tag contains a valid language subtag
     */
    public static Optional<Locale> parse(final String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        final String tag = value.trim().replace('_', '-');
        final Locale locale = Locale.forLanguageTag(tag);
        if (locale.getLanguage().isBlank() || "und".equalsIgnoreCase(locale.getLanguage())) {
            return Optional.empty();
        }
        final String normalized = locale.toLanguageTag();
        if (normalized.isBlank() || "und".equalsIgnoreCase(normalized)) {
            return Optional.empty();
        }
        return Optional.of(locale);
    }

    /**
     * @param locale a locale
     * @return a lowercase, hyphenated BCP-47 key suitable for map lookup, or {@code null}
     */
    public static String key(final Locale locale) {
        if (locale == null || locale.getLanguage().isBlank()) {
            return null;
        }
        return locale.stripExtensions().toLanguageTag().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns a stable, de-duplicated fallback chain. For example, {@code zh-Hant-TW} resolves as
     * {@code zh-Hant-TW -> zh-Hant -> zh-TW -> zh}.
     */
    public static List<Locale> fallbackChain(final Locale requested) {
        if (requested == null || requested.getLanguage().isBlank()) {
            return List.of();
        }

        final Locale locale = requested.stripExtensions();
        final Set<Locale> result = new LinkedHashSet<>();
        result.add(locale);
        if (!locale.getScript().isBlank()) {
            result.add(new Locale.Builder()
                    .setLanguage(locale.getLanguage())
                    .setScript(locale.getScript())
                    .build());
        }
        if (!locale.getCountry().isBlank()) {
            result.add(new Locale.Builder()
                    .setLanguage(locale.getLanguage())
                    .setRegion(locale.getCountry())
                    .build());
        }
        result.add(Locale.of(locale.getLanguage()));
        return List.copyOf(new ArrayList<>(result));
    }
}
