package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.api.GenericListMigrationReport;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class GenericListMigrationServiceTest {

    private final AemContext context = new AemContext();
    private final GenericListMigrationServiceImpl underTest = new GenericListMigrationServiceImpl();

    @Test
    void dryRunThenMigratesLegacyRowsAndTranslationsToCanonicalResource() {
        final Resource source = context.create().resource("/etc/acs-commons/lists/colors/jcr:content",
                "sling:resourceType", GenericListImpl.RT_ACS_COMMONS_GENERIC_LIST);
        context.create().resource("/etc/acs-commons/lists/colors/jcr:content/list/item0", Map.of(
                "jcr:title", "Red", "value", "red", "jcr:title.de", "Rot"));

        final GenericListMigrationReport dryRun = underTest.migrate(source, "/content/config/colors", false, true);
        assertTrue(dryRun.isDryRun());
        assertFalse(dryRun.isMigrated());
        assertEquals(1, dryRun.getMessages().stream().filter(message -> message.contains("1 valid")).count());
        assertFalse(context.resourceResolver().getResource("/content/config/colors") != null);

        final GenericListMigrationReport migrated = underTest.migrate(source, "/content/config/colors", false, false);
        final Resource target = context.resourceResolver().getResource("/content/config/colors");

        assertTrue(migrated.isMigrated());
        assertNotNull(target);
        assertEquals(GenericListImpl.RT_KEY_VALUE_LIST, target.getResourceType());
        assertEquals("Red", target.getChild("items/item0").getValueMap().get("jcr:title", String.class));
        assertEquals("de", target.getChild("items/item0/translations/item0").getValueMap().get("locale", String.class));
    }
}
