package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.api.GenericListSchema;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class GenericListImplTest {

    private final AemContext context = new AemContext();

    @Test
    void excludesMalformedAndDuplicateRowsSoIterationAndLookupAgree() {
        final Resource list = context.create().resource("/content/list", "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/list/items/valid", Map.of("jcr:title", "First", "value", "one"));
        context.create().resource("/content/list/items/blank", Map.of("jcr:title", " ", "value", "blank"));
        context.create().resource("/content/list/items/duplicate", Map.of("jcr:title", "Second", "value", "one"));
        context.create().resource("/content/list/items/no-value", Map.of("jcr:title", "Missing value"));

        final GenericListImpl underTest = new GenericListImpl(list);

        assertEquals(1, underTest.getItems().size());
        assertEquals("First", underTest.lookupTitle("one"));
        assertNull(underTest.lookupTitle("blank"));
        assertFalse(underTest.isValid());
        assertTrue(underTest.getValidationIssues().stream().anyMatch(issue -> "duplicate-value".equals(issue.getCode())));
        assertThrows(UnsupportedOperationException.class, () -> underTest.getItems().clear());
    }

    @Test
    void supportsBcp47ScriptAndRegionFallbackAndLegacyDottedProperties() {
        final Resource list = context.create().resource("/content/list");
        context.create().resource("/content/list/items/item0", Map.of(
                "jcr:title", "Default",
                "value", "first",
                "jcr:title.de", "Deutsch",
                "jcr:title.zh_hant", "Traditional"));
        context.create().resource("/content/list/items/item0/translations/item0", Map.of("locale", "zh-TW", "title", "Taiwan"));
        context.create().resource("/content/list/items/item0/translations/item1", Map.of("locale", "zh-Hant-TW", "title", "Traditional Taiwan"));

        final GenericListImpl underTest = new GenericListImpl(list);

        assertEquals("Traditional Taiwan", underTest.lookupTitle("first", Locale.forLanguageTag("zh-Hant-TW")));
        assertEquals("Traditional", underTest.lookupTitle("first", Locale.forLanguageTag("zh-Hant-HK")));
        assertEquals("Taiwan", underTest.lookupTitle("first", Locale.forLanguageTag("zh-Hans-TW")));
        assertEquals("Deutsch", underTest.lookupTitle("first", Locale.forLanguageTag("de-AT")));
        assertEquals("Default", underTest.lookupTitle("first", Locale.FRENCH));
    }

    @Test
    void exposesMetadataAndNormalizesSupportedLocales() {
        final Resource list = context.create().resource("/content/list", Map.of(
                GenericListSchema.PN_TITLE, "Countries",
                GenericListSchema.PN_DESCRIPTION, "Used for profile fields",
                GenericListSchema.PN_DEFAULT_LOCALE, "de-CH",
                GenericListSchema.PN_SUPPORTED_LOCALES, new String[]{"de_CH", "en-US"}));

        final GenericListImpl underTest = new GenericListImpl(list);

        assertEquals("Countries", underTest.getTitle());
        assertEquals("Used for profile fields", underTest.getDescription());
        assertEquals(Locale.forLanguageTag("de-CH"), underTest.getDefaultLocale());
        assertEquals(2, underTest.getSupportedLocales().size());
    }

    @Test
    void schemaReportsInvalidLocalesAndDuplicateTranslations() {
        final Resource list = context.create().resource("/content/list");
        context.create().resource("/content/list/items/item0", Map.of("jcr:title", "One", "value", "one"));
        context.create().resource("/content/list/items/item0/translations/item0", Map.of("locale", "not a locale", "title", "Bad"));
        context.create().resource("/content/list/items/item0/translations/item1", Map.of("locale", "de_CH", "title", "Deutsch"));
        context.create().resource("/content/list/items/item0/translations/item2", Map.of("locale", "de-CH", "title", "Duplicate"));

        final GenericListImpl underTest = new GenericListImpl(list);

        assertTrue(underTest.getValidationIssues().stream().anyMatch(issue -> "invalid-locale".equals(issue.getCode())));
        assertTrue(underTest.getValidationIssues().stream().anyMatch(issue -> "duplicate-locale".equals(issue.getCode())));
    }
}
