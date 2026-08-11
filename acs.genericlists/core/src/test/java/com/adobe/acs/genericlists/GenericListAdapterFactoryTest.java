package com.adobe.acs.genericlists;

import com.day.cq.wcm.api.Page;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
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
                "sling:resourceType", GenericListImpl.RT_GENERIC_LIST);
        context.create().resource("/content/my-list/jcr:content/list/item0",
                Map.of("jcr:title", "First", "value", "first"));

        final GenericList list = underTest.getAdapter(page, GenericList.class);

        assertEquals(1, list.getItems().size());
        assertEquals("First", list.getItems().get(0).getTitle());
    }
}
