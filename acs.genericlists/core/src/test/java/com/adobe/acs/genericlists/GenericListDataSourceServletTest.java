package com.adobe.acs.genericlists;

import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.EmptyDataSource;
import com.day.cq.wcm.api.Page;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Iterator;
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
                "sling:resourceType", GenericListImpl.RT_GENERIC_LIST);
        context.create().resource("/content/my-list/jcr:content/list/item0",
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
}
