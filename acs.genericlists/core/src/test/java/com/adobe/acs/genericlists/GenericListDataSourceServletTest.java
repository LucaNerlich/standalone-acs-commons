package com.adobe.acs.genericlists;

import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.EmptyDataSource;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class GenericListDataSourceServletTest {

    private final AemContext context = new AemContext();
    private final GenericListDataSourceServlet underTest = new GenericListDataSourceServlet();

    @Test
    void doGet_withNoPathProperty_yieldsEmptyDataSource() {
        context.create().resource("/content/field/datasource");
        context.currentResource("/content/field");

        underTest.doGet(context.request(), context.response());

        assertSame(EmptyDataSource.instance(), context.request().getAttribute(DataSource.class.getName()));
    }

    @Test
    void doGet_withUnresolvablePath_yieldsEmptyDataSource() {
        context.create().resource("/content/field/datasource", Map.of("path", "/content/does-not-exist"));
        context.currentResource("/content/field");

        underTest.doGet(context.request(), context.response());

        assertSame(EmptyDataSource.instance(), context.request().getAttribute(DataSource.class.getName()));
    }

    @Test
    void doGet_withGenericListPage_populatesDataSourceFromItems() {
        context.registerInjectActivateService(new GenericListAdapterFactory());
        context.create().page("/content/my-list", null,
                "sling:resourceType", GenericListImpl.RT_GENERIC_LIST_PAGE);
        context.create().resource("/content/my-list/jcr:content/root/keyValueList",
                "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/my-list/jcr:content/root/keyValueList/items/item0",
                Map.of("jcr:title", "First", "value", "first"));

        context.create().resource("/content/field/datasource", Map.of("path", "/content/my-list"));
        context.currentResource("/content/field");

        underTest.doGet(context.request(), context.response());

        final DataSource ds = (DataSource) context.request().getAttribute(DataSource.class.getName());
        final Iterator<Resource> iterator = ds.iterator();
        assertTrue(iterator.hasNext());
        final Resource option = iterator.next();
        assertEquals("first", option.getValueMap().get("value", String.class));
        assertEquals("First", option.getValueMap().get("text", String.class));
        assertFalse(iterator.hasNext());
    }

    @Test
    void doGet_withStandaloneGenericListResource_populatesDataSourceFromItems() {
        context.registerService(AdapterFactory.class, new GenericListResourceAdapterFactory(), Map.of(
                AdapterFactory.ADAPTABLE_CLASSES, new String[]{Resource.class.getName()},
                AdapterFactory.ADAPTER_CLASSES, new String[]{GenericList.class.getName()}));
        context.create().resource("/content/my-list",
                "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/my-list/items/item0",
                Map.of("jcr:title", "First", "value", "first"));
        context.create().resource("/content/my-list/items/item0/translations/item0",
                Map.of("locale", "de-CH", "title", "Erste"));
        context.create().resource("/content/field/datasource", Map.of("path", "/content/my-list"));
        context.currentResource("/content/field");
        context.request().setLocale(Locale.of("de", "CH"));

        underTest.doGet(context.request(), context.response());

        final Resource option = ((DataSource) context.request()
                .getAttribute(DataSource.class.getName())).iterator().next();
        assertEquals("first", option.getValueMap().get("value", String.class));
        assertEquals("Erste", option.getValueMap().get("text", String.class));
    }
}
