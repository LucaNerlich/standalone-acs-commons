package com.adobe.acs.include;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${{key:default}}} (optionally {@code ${{(Boolean|Long|Double)key:default}}}) placeholders in a
 * String against a flat parameters map. Stateless - kept separate from {@link ParameterizedResourceWrapper} so the
 * placeholder grammar can be read/tested on its own, independent of the resource-wrapping/recursion concerns.
 */
final class Placeholder {

    /*
     * Matches, e.g.:
     *   ${{fieldLabel:Text}}                  group(1)=null,      group(2)=fieldLabel, group(3)=Text
     *   ${{propertyName:./text}}              group(1)=null,      group(2)=propertyName, group(3)=./text
     *   ${{advanced}}                         group(1)=null,      group(2)=advanced,   group(3)=null (no default)
     *   ${{(Boolean)maximized:false}}         group(1)=Boolean,   group(2)=maximized,  group(3)=false
     *   ${{(Long)cols:0}}                     group(1)=Long,      group(2)=cols,       group(3)=0
     */
    private static final Pattern PATTERN =
            Pattern.compile("\\$\\{\\{(?:\\((\\w+)\\))?(\\w+)(?::([^{}]*))?}}");

    private Placeholder() {
    }

    /**
     * @param value      the (potentially placeholder-free) String to resolve
     * @param parameters the flat parameters map placeholder keys are looked up in
     * @return {@code value} with every {@code ${{key:default}}} replaced by {@code parameters.get(key)} (falling
     * back to the literal {@code default}, or an empty string), normalized per an optional {@code (Type)} prefix
     */
    static String resolve(final String value, final Map<String, String> parameters) {
        final Matcher matcher = PATTERN.matcher(value);
        if (!matcher.find()) {
            return value;
        }
        return matcher.replaceAll(result -> {
            final String typeHint = result.group(1);
            final String key = result.group(2);
            final String fallback = result.group(3) != null ? result.group(3) : "";
            final String chosen = parameters.getOrDefault(key, fallback);
            final String normalized = typeHint == null ? chosen : normalize(typeHint, chosen);
            return Matcher.quoteReplacement(normalized);
        });
    }

    /**
     * Normalizes a substituted value per an optional {@code (Type)} placeholder prefix, mirroring ACS Commons'
     * {@code TypeUtil.toObjectType} - the value stays a String, this only canonicalizes it (e.g. {@code "1,5"}
     * isn't a valid {@code Double}, so it's left as-is rather than crashing).
     */
    private static String normalize(final String typeHint, final String value) {
        try {
            return switch (typeHint.toLowerCase(Locale.ROOT)) {
                case "boolean" -> Boolean.toString(Boolean.parseBoolean(value));
                case "long" -> Long.toString(Long.parseLong(value));
                case "double" -> Double.toString(Double.parseDouble(value));
                default -> value;
            };
        } catch (final NumberFormatException e) {
            return value;
        }
    }
}
