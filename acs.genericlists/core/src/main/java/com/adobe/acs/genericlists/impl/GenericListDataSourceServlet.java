package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.GenericList;
import com.adobe.acs.genericlists.api.GenericListLocale;
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
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Granite datasource for canonical, in-house legacy, and ACS Commons Generic Lists.
 *
 * <p>The datasource node accepts {@code path}, optional {@code locale}, {@code sortBy} ({@code authored},
 * {@code title}, or {@code value}), {@code includeEmptyOption}, {@code emptyText}, and {@code disabledValues}.</p>
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = {
                GenericListDataSourceServlet.RESOURCE_TYPE,
                GenericListDataSourceServlet.LEGACY_RESOURCE_TYPE
        },
        methods = HttpConstants.METHOD_GET)
@ServiceDescription("Generic List Datasource Servlet")
public final class GenericListDataSourceServlet extends SlingSafeMethodsServlet {

    public static final String RESOURCE_TYPE = "acs-genericlists/components/utilities/genericlist/datasource";
    public static final String LEGACY_RESOURCE_TYPE = "acs-commons/components/utilities/genericlist/datasource";

    /** Allows the 1.x compatibility servlet facade to reuse the modern datasource behavior. */
    public void populate(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        doGet(request, response);
    }

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        request.setAttribute(DataSource.class.getName(), EmptyDataSource.instance());

        final ResourceResolver resolver = request.getResourceResolver();
        final ValueMap dataSourceProperties = ResourceUtil.getValueMap(request.getResource().getChild("datasource"));
        final String genericListPath = dataSourceProperties.get("path", String.class);
        if (genericListPath == null || genericListPath.isBlank()) {
            return;
        }

        final GenericList list = GenericListJsonSupport.fromResource(resolver.getResource(genericListPath));
        if (list == null) {
            return;
        }

        final Locale locale = resolveLocale(dataSourceProperties, request.getLocale());
        final Set<String> disabledValues = getStringSet(dataSourceProperties, "disabledValues");
        final List<GenericList.Item> items = new ArrayList<>(list.getItems());
        sort(items, dataSourceProperties.get("sortBy", "authored"), locale);

        final List<Resource> options = new ArrayList<>();
        if (dataSourceProperties.get("includeEmptyOption", false)) {
            final String emptyText = dataSourceProperties.get("emptyText", "");
            options.add(option(resolver, "", emptyText, false));
        }
        for (final GenericList.Item item : items) {
            options.add(option(resolver, item.getValue(), item.getTitle(locale), disabledValues.contains(item.getValue())));
        }
        request.setAttribute(DataSource.class.getName(), new SimpleDataSource(options.iterator()));
    }

    private static Resource option(
            final ResourceResolver resolver,
            final String value,
            final String text,
            final boolean disabled) {
        final ValueMap properties = new ValueMapDecorator(new HashMap<>());
        properties.put("value", value);
        properties.put("text", text);
        if (disabled) {
            properties.put("disabled", true);
        }
        return new ValueMapResource(resolver, new ResourceMetadata(), "nt:unstructured", properties);
    }

    private static Locale resolveLocale(final ValueMap properties, final Locale requestLocale) {
        final String configuredLocale = properties.get("locale", String.class);
        return GenericListLocale.parse(configuredLocale).orElse(requestLocale);
    }

    private static Set<String> getStringSet(final ValueMap properties, final String name) {
        final String[] values = properties.get(name, String[].class);
        if (values != null) {
            return new HashSet<>(List.of(values));
        }
        final String value = properties.get(name, String.class);
        return value == null ? Set.of() : new HashSet<>(List.of(value));
    }

    private static void sort(final List<GenericList.Item> items, final String sortBy, final Locale locale) {
        if ("title".equalsIgnoreCase(sortBy)) {
            final Collator collator = Collator.getInstance(locale);
            items.sort(Comparator.comparing(item -> item.getTitle(locale), collator));
        } else if ("value".equalsIgnoreCase(sortBy)) {
            items.sort(Comparator.comparing(GenericList.Item::getValue));
        }
    }
}
