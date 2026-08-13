package com.adobe.acs.genericlists;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class GenericListJsonResourceProviderTest {

    private final AemContext context = new AemContext();
    private final GenericListJsonResourceProvider underTest = new GenericListJsonResourceProvider();
    private ResolveContext<Object> resolveContext;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        context.registerService(AdapterFactory.class, new GenericListResourceAdapterFactory(), Map.of(
                AdapterFactory.ADAPTABLE_CLASSES, new String[]{Resource.class.getName()},
                AdapterFactory.ADAPTER_CLASSES, new String[]{GenericList.class.getName()}));
        final GenericListJsonResourceProvider.Config config = mock(GenericListJsonResourceProvider.Config.class);
        when(config.list_root()).thenReturn(GenericListJsonResourceProvider.DEFAULT_LIST_ROOT);
        underTest.activate(config);

        resolveContext = mock(ResolveContext.class);
        when(resolveContext.getResourceResolver()).thenReturn(context.resourceResolver());
    }

    @Test
    void getResource_exposesStandaloneListAsOptionsJson() throws IOException {
        context.create().resource("/etc/acs-commons/lists/colors",
                "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);
        context.create().resource("/etc/acs-commons/lists/colors/items/item0",
                Map.of("jcr:title", "Red", "value", "red"));

        final Resource resource = underTest.getResource(
                resolveContext, "/mnt/acs-commons/lists/colors.json", null, null);

        assertNotNull(resource);
        assertEquals("application/json", resource.getResourceMetadata().getContentType());
        assertEquals("UTF-8", resource.getResourceMetadata().getCharacterEncoding());
        try (InputStream stream = resource.adaptTo(InputStream.class)) {
            assertNotNull(stream);
            assertEquals(
                    "{\"options\":[{\"text\":\"Red\",\"title\":\"Red\",\"value\":\"red\"}]}",
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void getResource_returnsNullForUnknownList() {
        assertNull(underTest.getResource(
                resolveContext, "/mnt/acs-commons/lists/unknown.json", null, null));
    }

    @Test
    void getResource_acceptsPathWithoutJsonExtension() {
        context.create().resource("/etc/acs-commons/lists/colors",
                "sling:resourceType", GenericListImpl.RT_KEY_VALUE_LIST);

        assertNotNull(underTest.getResource(
                resolveContext, "/mnt/acs-commons/lists/colors", null, null));
    }

    @Test
    void getResource_rejectsTraversalPath() {
        assertNull(underTest.getResource(
                resolveContext, "/mnt/acs-commons/lists/../secret.json", null, null));
    }
}
