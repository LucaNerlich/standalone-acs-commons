package com.adobe.acs.include;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(AemContextExtension.class)
class ParameterizedResourceWrapperTest {

    public final AemContext context = new AemContext();

    @Test
    void wrap_substitutesSuppliedParameter() {
        final Resource target = context.create().resource("/content/target",
                Map.of("fieldLabel", "${{fieldLabel:Default Label}}"));
        final Resource parameters = context.create().resource("/content/parameters",
                Map.of("fieldLabel", "Custom Label"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, parameters);

        assertEquals("Custom Label", wrapped.getValueMap().get("fieldLabel", String.class));
    }

    @Test
    void wrap_fallsBackToLiteralDefaultWhenParameterMissing() {
        final Resource target = context.create().resource("/content/target",
                Map.of("name", "${{propertyName:./defaultProp}}"));
        final Resource parameters = context.create().resource("/content/parameters",
                Map.of("unrelatedKey", "value"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, parameters);

        assertEquals("./defaultProp", wrapped.getValueMap().get("name", String.class));
    }

    @Test
    void wrap_fallsBackToLiteralDefaultWhenParametersResourceIsNull() {
        final Resource target = context.create().resource("/content/target",
                Map.of("fieldLabel", "${{fieldLabel:Default Label}}"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, null);

        assertEquals("Default Label", wrapped.getValueMap().get("fieldLabel", String.class));
    }

    @Test
    void wrap_leavesNonPlaceholderPropertiesUnchanged() {
        final Resource target = context.create().resource("/content/target",
                Map.of("sling:resourceType", "granite/ui/components/coral/foundation/form/textfield"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, null);

        assertEquals("granite/ui/components/coral/foundation/form/textfield", wrapped.getResourceType());
    }

    @Test
    void wrap_appliesRecursivelyToChildren() {
        final Resource target = context.create().resource("/content/target");
        context.create().resource("/content/target/child",
                Map.of("fieldLabel", "${{fieldLabel:Child Default}}"));
        final Resource parameters = context.create().resource("/content/parameters",
                Map.of("fieldLabel", "Custom Label"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, parameters);
        final Resource wrappedChild = wrapped.getChild("child");

        assertEquals("Custom Label", wrappedChild.getValueMap().get("fieldLabel", String.class));
    }

    @Test
    void wrap_returnsNullForMissingChild() {
        final Resource target = context.create().resource("/content/target");

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, null);

        assertNull(wrapped.getChild("doesNotExist"));
    }

    @Test
    void cascadeNamespace_usesOwnNamespaceWhenNoAmbientOne() {
        final Resource plainResource = context.create().resource("/content/plain");

        assertEquals("block1", ParameterizedResourceWrapper.cascadeNamespace(plainResource, "block1"));
    }

    @Test
    void cascadeNamespace_usesAmbientNamespaceWhenOwnOneIsAbsent() {
        final Resource target = context.create().resource("/content/target");
        final Resource ambient = ParameterizedResourceWrapper.wrap(target, null, "outer");

        assertEquals("outer", ParameterizedResourceWrapper.cascadeNamespace(ambient, null));
    }

    @Test
    void cascadeNamespace_combinesAmbientAndOwnNamespace() {
        final Resource target = context.create().resource("/content/target");
        final Resource ambient = ParameterizedResourceWrapper.wrap(target, null, "outer");

        assertEquals("outer/inner", ParameterizedResourceWrapper.cascadeNamespace(ambient, "inner"));
    }

    @Test
    void wrap_withoutNamespace_leavesNamespacedPropertiesUnchanged() {
        final Resource target = context.create().resource("/content/target", Map.of("name", "./text"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, null);

        assertEquals("./text", wrapped.getValueMap().get("name", String.class));
    }

    @Test
    void wrap_withNamespace_prefixesRelativeName() {
        final Resource target = context.create().resource("/content/target", Map.of("name", "./text"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, null, "block1");

        assertEquals("./block1/text", wrapped.getValueMap().get("name", String.class));
    }

    @Test
    void wrap_withNamespace_prefixesAbsoluteName() {
        final Resource target = context.create().resource("/content/target", Map.of("name", "text"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, null, "block1");

        assertEquals("block1/text", wrapped.getValueMap().get("name", String.class));
    }

    @Test
    void wrap_withNamespace_cascadesToChildren() {
        final Resource target = context.create().resource("/content/target");
        context.create().resource("/content/target/child", Map.of("name", "./child"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, null, "block1");
        final Resource wrappedChild = wrapped.getChild("child");

        assertEquals("./block1/child", wrappedChild.getValueMap().get("name", String.class));
    }

    @Test
    void wrap_withNamespace_doesNotCascadeIntoMultifield() {
        final Resource target = context.create().resource("/content/target");
        final Resource multifield = context.create().resource("/content/target/pages",
                Map.of("sling:resourceType", "granite/ui/components/coral/foundation/form/multifield"));
        context.create().resource("/content/target/pages/child", Map.of("name", "./child"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, null, "block1");
        final Resource wrappedMultifield = wrapped.getChild("pages");
        final Resource wrappedChild = wrappedMultifield.getChild("child");

        assertEquals("./child", wrappedChild.getValueMap().get("name", String.class));
    }

    @Test
    void wrap_hidesChildWhenHidePropertyIsTrue() {
        final Resource target = context.create().resource("/content/target");
        context.create().resource("/content/target/child", Map.of("hide", "true"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, null);

        assertNull(wrapped.getChild("child"));
    }

    @Test
    void wrap_hidesChildWhenHidePlaceholderResolvesTrue() {
        final Resource target = context.create().resource("/content/target");
        context.create().resource("/content/target/child", Map.of("hide", "${{advanced:false}}"));
        final Resource parameters = context.create().resource("/content/parameters",
                Map.of("advanced", "true"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, parameters);

        assertNull(wrapped.getChild("child"));
    }

    @Test
    void wrap_keepsChildWhenHidePropertyIsAbsent() {
        final Resource target = context.create().resource("/content/target");
        context.create().resource("/content/target/child", Map.of());

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, null);

        assertNotNull(wrapped.getChild("child"));
    }

    @Test
    void wrap_listChildrenExcludesHiddenSiblings() {
        final Resource target = context.create().resource("/content/target");
        context.create().resource("/content/target/visible", Map.of());
        context.create().resource("/content/target/hidden", Map.of("hide", "true"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, null);
        final Iterator<Resource> children = wrapped.listChildren();
        int count = 0;
        while (children.hasNext()) {
            assertEquals("visible", children.next().getName());
            count++;
        }

        assertEquals(1, count);
    }

    @Test
    void wrap_typedCastNormalizesBoolean() {
        final Resource target = context.create().resource("/content/target",
                Map.of("maximized", "${{(Boolean)maximized:false}}"));
        final Resource parameters = context.create().resource("/content/parameters",
                Map.of("maximized", "TRUE"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, parameters);

        assertEquals("true", wrapped.getValueMap().get("maximized", String.class));
    }

    @Test
    void wrap_typedCastNormalizesLong() {
        final Resource target = context.create().resource("/content/target",
                Map.of("cols", "${{(Long)cols:0}}"));
        final Resource parameters = context.create().resource("/content/parameters",
                Map.of("cols", "3"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, parameters);

        assertEquals("3", wrapped.getValueMap().get("cols", String.class));
    }

    @Test
    void wrap_typedCastFallsBackToRawValueOnInvalidNumber() {
        final Resource target = context.create().resource("/content/target",
                Map.of("cols", "${{(Long)cols:0}}"));
        final Resource parameters = context.create().resource("/content/parameters",
                Map.of("cols", "not-a-number"));

        final Resource wrapped = ParameterizedResourceWrapper.wrap(target, parameters);

        assertEquals("not-a-number", wrapped.getValueMap().get("cols", String.class));
    }
}
