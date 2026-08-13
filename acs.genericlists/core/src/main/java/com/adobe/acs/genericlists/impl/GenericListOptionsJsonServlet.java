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

/** Localizes the JSON emitted by the /mnt compatibility resource provider for HTTP requests. */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = GenericListJsonResourceProvider.JSON_RESOURCE_TYPE,
        methods = HttpConstants.METHOD_GET)
@ServiceDescription("Generic List Options JSON Servlet")
public final class GenericListOptionsJsonServlet extends SlingSafeMethodsServlet {

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException {
        final GenericList list = request.getResource().adaptTo(GenericList.class);
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
        response.getWriter().write(GenericListJsonSupport.toOptionsJson(list, locale));
    }
}
