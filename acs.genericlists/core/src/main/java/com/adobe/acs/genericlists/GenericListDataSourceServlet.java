package com.adobe.acs.genericlists;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;

import javax.servlet.Servlet;

/** @deprecated Compatibility servlet facade. The modern implementation remains behavior-compatible. */
@Deprecated(since = "1.3.0", forRemoval = false)
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = GenericListDataSourceServlet.RESOURCE_TYPE,
        methods = HttpConstants.METHOD_GET)
@ServiceDescription("Generic List Datasource Servlet")
public class GenericListDataSourceServlet extends SlingSafeMethodsServlet {

    public static final String RESOURCE_TYPE = "acs-genericlists/components/utilities/genericlist/datasource";

    private final com.adobe.acs.genericlists.impl.GenericListDataSourceServlet delegate =
            new com.adobe.acs.genericlists.impl.GenericListDataSourceServlet();

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        delegate.populate(request, response);
    }
}
