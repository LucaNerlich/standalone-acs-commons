package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.GenericList;
import com.adobe.granite.ui.components.ds.DataSource;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class GenericListDataSourceServletTest {

    private final AemContext context = new AemContext();
    private final GenericListDataSourceServlet underTest = new GenericListDataSourceServlet();

    @Test
    void supportsLocalizedSortingEmptyOptionAndDisabledValues() {
        context.registerService(AdapterFactory.class, new GenericListResourceAdapterFactory(), Map.of(
                AdapterFactory.ADAPTABLE_CLASSES, new String[]{Resource.class.getName()},
                AdapterFactory.ADAPTER_CLASSES, new String[]{GenericList.class.getName(), com.adobe.acs.genericlists.api.GenericList.class.getName()}));
        context.create().resource("/content/list", "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/list/items/item0", Map.of("jcr:title", "Zulu", "value", "z"));
        context.create().resource("/content/list/items/item0/translations/item0", Map.of("locale", "de", "title", "Zebra"));
        context.create().resource("/content/list/items/item1", Map.of("jcr:title", "Alpha", "value", "a"));
        context.create().resource("/content/field/datasource", Map.of(
                "path", "/content/list",
                "locale", "de",
                "sortBy", "value",
                "includeEmptyOption", true,
                "emptyText", "Choose one",
                "disabledValues", new String[]{"z"}));
        context.currentResource("/content/field");
        context.request().setLocale(Locale.ENGLISH);

        underTest.doGet(context.request(), context.response());

        final Iterator<Resource> options = ((DataSource) context.request().getAttribute(DataSource.class.getName())).iterator();
        assertEquals("", options.next().getValueMap().get("value", String.class));
        final Resource alpha = options.next();
        assertEquals("a", alpha.getValueMap().get("value", String.class));
        final Resource zulu = options.next();
        assertEquals("Zebra", zulu.getValueMap().get("text", String.class));
        assertTrue(zulu.getValueMap().get("disabled", false));
        assertFalse(options.hasNext());
    }
}
