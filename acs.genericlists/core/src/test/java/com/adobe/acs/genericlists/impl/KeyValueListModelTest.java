package com.adobe.acs.genericlists.impl;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class KeyValueListModelTest {

    private final AemContext context = new AemContext();

    @BeforeEach
    void setUp() {
        context.addModelsForClasses(KeyValueListModel.class);
    }

    @Test
    void htlPresentationModelOnlyResolvesForCanonicalComponent() {
        context.create().resource("/content/list", "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/content/list/items/item0", Map.of("jcr:title", "First", "value", "first"));

        final KeyValueListModel model = context.resourceResolver().getResource("/content/list").adaptTo(KeyValueListModel.class);

        assertNotNull(model);
        assertTrue(model.isRenderable());
        assertEquals("First", model.getItems().getFirst().getTitle());
    }
}
