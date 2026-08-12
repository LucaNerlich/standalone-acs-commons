package com.adobe.acs.genericlists;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class GenericListImplTest {

    private final AemContext context = new AemContext();

    @Test
    void nullListResource_yieldsEmptyList() {
        final GenericListImpl underTest = new GenericListImpl(null);

        assertTrue(underTest.getItems().isEmpty());
        assertNull(underTest.lookupTitle("anything"));
    }

    @Test
    void getItems_returnsAllChildrenWithATitle() {
        final Resource list = context.create().resource("/content/list");
        context.create().resource("/content/list/item0", Map.of("jcr:title", "First", "value", "first"));
        context.create().resource("/content/list/item1", Map.of("jcr:title", "Second", "value", "second"));
        // no jcr:title -> skipped
        context.create().resource("/content/list/item2", Map.of("value", "third"));

        final GenericListImpl underTest = new GenericListImpl(list);

        final List<GenericList.Item> items = underTest.getItems();
        assertEquals(2, items.size());
        assertEquals("First", items.get(0).getTitle());
        assertEquals("first", items.get(0).getValue());
        assertEquals("Second", items.get(1).getTitle());
    }

    @Test
    void getItems_readsFromNestedItemNode_whenPresent() {
        // this is the shape the "list" component's multifield dialog (name="./item") actually produces
        final Resource list = context.create().resource("/content/list");
        context.create().resource("/content/list/item/item0", Map.of("jcr:title", "First", "value", "first"));
        context.create().resource("/content/list/item/item1", Map.of("jcr:title", "Second", "value", "second"));

        final GenericListImpl underTest = new GenericListImpl(list);

        final List<GenericList.Item> items = underTest.getItems();
        assertEquals(2, items.size());
        assertEquals("First", items.get(0).getTitle());
        assertEquals("first", items.get(0).getValue());
    }

    @Test
    void lookupTitle_returnsTitleForKnownValue_andNullForUnknown() {
        final Resource list = context.create().resource("/content/list");
        context.create().resource("/content/list/item0", Map.of("jcr:title", "First", "value", "first"));

        final GenericListImpl underTest = new GenericListImpl(list);

        assertEquals("First", underTest.lookupTitle("first"));
        assertNull(underTest.lookupTitle("does-not-exist"));
    }

    @Test
    void lookupTitle_withLocale_fallsBackToLanguageOnlyThenDefaultTitle() {
        final Resource list = context.create().resource("/content/list");
        context.create().resource("/content/list/item0", Map.of(
                "jcr:title", "Default",
                "value", "first",
                "jcr:title.de", "Standard"));

        final GenericListImpl underTest = new GenericListImpl(list);

        // exact country match (de_at) not present -> falls back to language-only (de)
        assertEquals("Standard", underTest.lookupTitle("first", Locale.of("de", "AT")));
        // no french translation at all -> falls back to the default title
        assertEquals("Default", underTest.lookupTitle("first", Locale.FRENCH));
        // no locale -> default title
        assertEquals("Default", underTest.lookupTitle("first", null));
    }

    @Test
    void lookupTitle_withLocale_prefersExactCountryMatch() {
        final Resource list = context.create().resource("/content/list");
        context.create().resource("/content/list/item0", Map.of(
                "jcr:title", "Default",
                "value", "first",
                "jcr:title.de", "Standard",
                "jcr:title.de_ch", "Schweizer Standard"));

        final GenericListImpl underTest = new GenericListImpl(list);

        assertEquals("Schweizer Standard", underTest.lookupTitle("first", Locale.of("de", "CH")));
    }
}
