package com.adobe.acs.genericlists;

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
    private final GenericListAdapterFactory underTest = new GenericListAdapterFactory();

    @Test
    void getAdapter_returnsNullForNonGenericListType() {
        final Page page = context.create().page("/content/my-list");

        assertNull(underTest.getAdapter(page, String.class));
    }

    @Test
    void getAdapter_returnsNullForNonPageAdaptable() {
        assertNull(underTest.getAdapter("not-a-page", GenericList.class));
    }

    @Test
    void getAdapter_returnsNullWhenPageIsNotAGenericListPage() {
        final Page page = context.create().page("/content/some-other-page");

        assertNull(underTest.getAdapter(page, GenericList.class));
    }

    @Test
    void getAdapter_returnsGenericListForAMatchingPage() {
        final Page page = context.create().page("/content/my-list", null,
                "sling:resourceType", GenericListImpl.RT_GENERIC_LIST_PAGE);
        context.create().resource("/content/my-list/jcr:content/root/keyValueList",
                "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/my-list/jcr:content/root/keyValueList/items/item0",
                Map.of("jcr:title", "First", "value", "first"));

        final GenericList list = underTest.getAdapter(page, GenericList.class);

        assertEquals(1, list.getItems().size());
        assertEquals("First", list.getItems().get(0).getTitle());
    }

    @Test
    void getAdapter_returnsNullWhenKeyValueListComponentIsMissing() {
        final Page page = context.create().page("/content/my-list", null,
                "sling:resourceType", GenericListImpl.RT_GENERIC_LIST_PAGE);

        assertNull(underTest.getAdapter(page, GenericList.class));
    }

    @Test
    void getAdapter_keepsSupportingLegacyGenericListPages() {
        context.create().resource("/apps/acs-genericlists/components/utilities/genericlist",
                "sling:resourceSuperType", GenericListImpl.RT_GENERIC_LIST_PAGE);
        final Page page = context.create().page("/content/my-list", null,
                "sling:resourceType", GenericListImpl.RT_LEGACY_GENERIC_LIST);
        context.create().resource("/content/my-list/jcr:content/list/item/item0",
                Map.of("jcr:title", "First", "value", "first"));

        final GenericList list = underTest.getAdapter(page, GenericList.class);

        assertEquals("First", list.getItems().get(0).getTitle());
    }

    @Test
    void getAdapter_adaptsStandaloneKeyValueListResource() {
        final GenericListResourceAdapterFactory resourceAdapter = new GenericListResourceAdapterFactory();
        final Resource resource = context.create().resource("/content/my-list",
                "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/my-list/items/item0",
                Map.of("jcr:title", "First", "value", "first"));

        final GenericList list = resourceAdapter.getAdapter(resource, GenericList.class);

        assertEquals("First", list.getItems().get(0).getTitle());
    }

    @Test
    void getAdapter_adaptsNewPageContentResourceDirectly() {
        final GenericListResourceAdapterFactory resourceAdapter = new GenericListResourceAdapterFactory();
        final Page page = context.create().page("/content/my-list", null,
                "sling:resourceType", GenericListImpl.RT_GENERIC_LIST_PAGE);
        context.create().resource("/content/my-list/jcr:content/root/keyValueList",
                "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/my-list/jcr:content/root/keyValueList/items/item0",
                Map.of("jcr:title", "First", "value", "first"));

        final GenericList list = resourceAdapter.getAdapter(page.getContentResource(), GenericList.class);

        assertEquals("First", list.getItems().get(0).getTitle());
    }

    @Test
    void getAdapter_doesNotSupportOriginalAcsCommonsResourceType() {
        final GenericListResourceAdapterFactory resourceAdapter = new GenericListResourceAdapterFactory();
        final Resource resource = context.create().resource("/content/old-list",
                "sling:resourceType", "acs-commons/components/utilities/genericlist");
        context.create().resource("/content/old-list/list/item0",
                Map.of("jcr:title", "First", "value", "first"));

        assertNull(resourceAdapter.getAdapter(resource, GenericList.class));
    }
}
