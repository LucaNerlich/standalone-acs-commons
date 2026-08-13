package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.GenericList;
import com.day.cq.wcm.api.Page;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(AemContextExtension.class)
class GenericListAdapterFactoryTest {

    private final AemContext context = new AemContext();
    private final GenericListAdapterFactory pageAdapter = new GenericListAdapterFactory();
    private final GenericListResourceAdapterFactory resourceAdapter = new GenericListResourceAdapterFactory();

    @Test
    void adaptsCanonicalResourceToBothSupportedApis() {
        final Resource resource = context.create().resource("/content/list", "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/list/items/item0", Map.of("jcr:title", "First", "value", "first"));

        final GenericList legacyApi = resourceAdapter.getAdapter(resource, GenericList.class);
        final com.adobe.acs.genericlists.api.GenericList api = resourceAdapter.getAdapter(
                resource, com.adobe.acs.genericlists.api.GenericList.class);

        assertEquals("First", legacyApi.lookupTitle("first"));
        assertEquals("First", api.lookupTitle("first"));
    }

    @Test
    void adaptsOriginalAcsCommonsContentForMigrationCompatibility() {
        final Resource resource = context.create().resource("/etc/acs-commons/lists/colors/jcr:content",
                "sling:resourceType", GenericListImpl.RT_ACS_COMMONS_GENERIC_LIST);
        context.create().resource("/etc/acs-commons/lists/colors/jcr:content/list/item0",
                Map.of("jcr:title", "Red", "value", "red"));

        final GenericList list = resourceAdapter.getAdapter(resource, GenericList.class);

        assertEquals("Red", list.lookupTitle("red"));
    }

    @Test
    void supportsExplicitPathAndCustomizedPageComponentLocations() {
        context.create().resource("/content/config/colors", "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/config/colors/items/item0", Map.of("jcr:title", "Red", "value", "red"));
        final Page page = context.create().page("/content/page", null,
                "sling:resourceType", GenericListImpl.RT_GENERIC_LIST_PAGE,
                GenericListAdapterFactory.PN_LIST_RESOURCE_PATH, "/content/config/colors");

        final GenericList list = pageAdapter.getAdapter(page, GenericList.class);

        assertEquals("Red", list.lookupTitle("red"));
    }

    @Test
    void refusesAmbiguousCustomizedPages() {
        final Page page = context.create().page("/content/page", null,
                "sling:resourceType", GenericListImpl.RT_GENERIC_LIST_PAGE);
        context.create().resource("/content/page/jcr:content/root/one", "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/page/jcr:content/root/two", "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);

        assertNull(pageAdapter.getAdapter(page, GenericList.class));
    }
}
