package com.adobe.acs.genericlists;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(AemContextExtension.class)
class GenericListJsonServletTest {

    private final AemContext context = new AemContext();
    private final GenericListJsonServlet underTest = new GenericListJsonServlet();

    @Test
    void doGet_serializesStandaloneListInAuthoredOrder() throws IOException {
        context.registerService(AdapterFactory.class, new GenericListResourceAdapterFactory(), Map.of(
                AdapterFactory.ADAPTABLE_CLASSES, new String[]{Resource.class.getName()},
                AdapterFactory.ADAPTER_CLASSES, new String[]{GenericList.class.getName()}));
        context.create().resource("/content/list",
                "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/list/items/item0",
                Map.of("jcr:title", "First", "value", "first"));
        context.create().resource("/content/list/items/item1",
                Map.of("jcr:title", "Second", "value", "second"));
        context.currentResource("/content/list");
        final MockSlingHttpServletResponse response = context.response();

        underTest.doGet(context.request(), response);

        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertEquals(
                "[{\"value\":\"first\",\"text\":\"First\"},{\"value\":\"second\",\"text\":\"Second\"}]",
                response.getOutputAsString());
    }

    @Test
    void doGet_returnsNotFoundForNonListResource() throws IOException {
        context.currentResource(context.create().resource("/content/not-a-list"));
        final MockSlingHttpServletResponse response = context.response();

        underTest.doGet(context.request(), response);

        assertEquals(404, response.getStatus());
    }
}
