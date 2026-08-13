package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.api.GenericListLocale;
import org.apache.sling.api.SlingHttpServletRequest;

import java.util.Locale;

/** Request locale parsing shared by JSON and datasource delivery. */
final class GenericListRequestLocale {

    private GenericListRequestLocale() {
    }

    static Locale resolve(final SlingHttpServletRequest request) throws InvalidLocaleException {
        final String requestedLocale = request.getParameter("locale");
        if (requestedLocale == null || requestedLocale.isBlank()) {
            return request.getLocale();
        }
        return GenericListLocale.parse(requestedLocale)
                .orElseThrow(() -> new InvalidLocaleException(requestedLocale));
    }

    static final class InvalidLocaleException extends Exception {
        private InvalidLocaleException(final String locale) {
            super("Invalid BCP-47 locale: " + locale);
        }
    }
}
