package com.adobe.acs.genericlists;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;

import javax.servlet.Servlet;
import java.io.IOException;

@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = {
                GenericListImpl.RT_GENERIC_LIST_PAGE,
                GenericListImpl.RT_LEGACY_GENERIC_LIST,
                GenericListImpl.RT_KEY_VALUE_LIST
        },
        selectors = "list",
        extensions = "json",
        methods = HttpConstants.METHOD_GET)
@ServiceDescription("Generic List JSON Servlet")
public class GenericListJsonServlet extends SlingSafeMethodsServlet {

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException {
        final GenericList list = GenericListJsonSupport.fromResource(request.getResource());
        if (list == null) {
            response.setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(GenericListJsonSupport.toListJson(list));
    }
}
