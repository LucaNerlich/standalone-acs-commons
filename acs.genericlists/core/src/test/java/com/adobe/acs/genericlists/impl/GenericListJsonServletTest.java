package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.GenericList;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(AemContextExtension.class)
class GenericListJsonServletTest {

    private final AemContext context = new AemContext();
    private final GenericListJsonServlet underTest = new GenericListJsonServlet();

    @Test
    void serializesLocalizedListJsonAndSetsLanguageHeaders() throws IOException {
        context.registerService(AdapterFactory.class, new GenericListResourceAdapterFactory(), Map.of(
                AdapterFactory.ADAPTABLE_CLASSES, new String[]{Resource.class.getName()},
                AdapterFactory.ADAPTER_CLASSES, new String[]{GenericList.class.getName(), com.adobe.acs.genericlists.api.GenericList.class.getName()}));
        context.create().resource("/content/list", "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/list/items/item0", Map.of("jcr:title", "Default", "value", "one"));
        context.create().resource("/content/list/items/item0/translations/item0", Map.of("locale", "de-CH", "title", "Deutsch"));
        context.currentResource("/content/list");
        context.request().setLocale(Locale.forLanguageTag("de-CH"));
        final MockSlingHttpServletResponse response = context.response();

        underTest.doGet(context.request(), response);

        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertEquals("Accept-Language", response.getHeader("Vary"));
        assertEquals("de-CH", response.getHeader("Content-Language"));
        assertEquals("[{\"value\":\"one\",\"text\":\"Deutsch\"}]", response.getOutputAsString());
    }
}
