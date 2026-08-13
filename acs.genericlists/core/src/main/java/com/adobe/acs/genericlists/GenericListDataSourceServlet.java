package com.adobe.acs.genericlists;

import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.EmptyDataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;

import javax.servlet.Servlet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * In-house replacement for ACS AEM Commons' Generic Lists datasource JSP. Feeds a Granite UI select/radiogroup's
 * options from a standalone Generic List resource or an authoring page that contains one: set the field's own
 * {@code sling:resourceType} to {@link #RESOURCE_TYPE} and point its {@code datasource} child node's {@code path}
 * property at the resource or page.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = GenericListDataSourceServlet.RESOURCE_TYPE,
        methods = HttpConstants.METHOD_GET)
@ServiceDescription("Generic List Datasource Servlet")
public class GenericListDataSourceServlet extends SlingSafeMethodsServlet {

    public static final String RESOURCE_TYPE = "acs-genericlists/components/utilities/genericlist/datasource";

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        request.setAttribute(DataSource.class.getName(), EmptyDataSource.instance());

        final ResourceResolver resolver = request.getResourceResolver();
        final ValueMap dsProperties = ResourceUtil.getValueMap(request.getResource().getChild("datasource"));
        final String genericListPath = dsProperties.get("path", String.class);
        if (genericListPath == null) {
            return;
        }

        final GenericList list = GenericListJsonSupport.fromResource(resolver.getResource(genericListPath));
        if (list == null) {
            return;
        }

        final Locale locale = request.getLocale();
        final List<Resource> options = new ArrayList<>();
        for (final GenericList.Item item : list.getItems()) {
            final ValueMap vm = new ValueMapDecorator(new HashMap<>());
            vm.put("value", item.getValue());
            vm.put("text", item.getTitle(locale));
            options.add(new ValueMapResource(resolver, new ResourceMetadata(), "nt:unstructured", vm));
        }

        request.setAttribute(DataSource.class.getName(), new SimpleDataSource(options.iterator()));
    }
}
