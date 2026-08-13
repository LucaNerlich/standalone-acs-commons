package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.GenericList;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Locale;

/** Delivers the ACS-compatible {@code .list.json} array contract with explicit BCP-47 localization support. */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = {
                GenericListImpl.RT_GENERIC_LIST_PAGE,
                GenericListImpl.RT_LEGACY_GENERIC_LIST,
                GenericListImpl.RT_ACS_COMMONS_GENERIC_LIST,
                GenericListImpl.RT_KEY_VALUE_LIST
        },
        selectors = "list",
        extensions = "json",
        methods = HttpConstants.METHOD_GET)
@ServiceDescription("Generic List JSON Servlet")
public final class GenericListJsonServlet extends SlingSafeMethodsServlet {

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException {
        final GenericList list = GenericListJsonSupport.fromResource(request.getResource());
        if (list == null) {
            response.setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
            return;
        }

        final Locale locale;
        try {
            locale = GenericListRequestLocale.resolve(request);
        } catch (GenericListRequestLocale.InvalidLocaleException ex) {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"invalid-locale\"}");
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Vary", "Accept-Language");
        if (locale != null) {
            response.setHeader("Content-Language", locale.toLanguageTag());
        }
        response.getWriter().write(GenericListJsonSupport.toListJson(list, locale));
    }
}
