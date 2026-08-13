package com.adobe.acs.genericlists.api;

import org.osgi.annotation.versioning.ProviderType;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stable modern Generic List API.
 *
 * <p>This API extends the 1.x standalone contract so existing item values remain source and binary compatible.
 * The canonical representation is a resource of type {@code acs-genericlists/components/key-value-list}.
 * Implementations return only rows that satisfy {@link GenericListSchema}; malformed repository data remains
 * observable through {@link #getValidationIssues()}.</p>
 */
@ProviderType
public interface GenericList extends com.adobe.acs.genericlists.GenericList {

    /**
     * @return diagnostics for malformed rows and metadata. A non-empty result does not prevent valid rows from
     *         being consumed.
     */
    default List<GenericListValidationIssue> getValidationIssues() {
        return Collections.emptyList();
    }

    /** @return {@code true} when the backing resource satisfies the Generic List schema */
    default boolean isValid() {
        return getValidationIssues().isEmpty();
    }

    /** @return optional display title, or {@code null} */
    default String getTitle() {
        return null;
    }

    /** @return optional description, or {@code null} */
    default String getDescription() {
        return null;
    }

    /** @return configured default locale, or {@code null} */
    default Locale getDefaultLocale() {
        return null;
    }

    /** @return configured supported locales, normalized to BCP-47; never {@code null} */
    default Set<Locale> getSupportedLocales() {
        return Collections.emptySet();
    }
}
