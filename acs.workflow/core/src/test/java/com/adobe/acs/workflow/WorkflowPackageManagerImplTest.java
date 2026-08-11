package com.adobe.acs.workflow;

import com.day.cq.wcm.api.Page;
import com.day.cq.workflow.collection.ResourceCollection;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.jcr.Node;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class WorkflowPackageManagerImplTest {

    // JCR_MOCK, not the default resolver, since create()/delete() need a real adaptTo(Session.class)/Node -
    // JCR_MOCK implements the plain javax.jcr API without pulling in a full Oak repository.
    private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

    private final WorkflowPackageManagerImpl underTest = new WorkflowPackageManagerImpl();

    /**
     * On a real AEMaaCS instance /var/workflow/packages already exists; a bare test repo doesn't have it, so
     * seed it explicitly wherever a test wants the modern (non-legacy) bucket path.
     */
    private void seedModernBucketRoot() {
        context.create().resource("/var/workflow/packages");
    }

    @Test
    void create_usesLegacyBucketPathWhenModernRootIsMissing() throws Exception {
        final Page page = underTest.create(context.resourceResolver(), "my-package", new String[]{"/content/foo"});

        assertTrue(page.getPath().startsWith("/etc/workflow/packages/"));
    }

    @Test
    void create_buildsWorkflowPackagePageWithFilterDefinition() throws Exception {
        seedModernBucketRoot();

        // Note: paths must be passed as an explicit String[] here - create() is overloaded with two varargs
        // methods differing only in fixed arity, so 2+ bare trailing String args are ambiguous to javac.
        final Page page = underTest.create(context.resourceResolver(), "bulk-publish", "my-package",
                new String[]{"/content/foo", "/content/bar"});

        assertNotNull(page);
        assertTrue(page.getPath().startsWith("/var/workflow/packages/bulk-publish/"));

        final Resource filter = page.getContentResource().getChild("vlt:definition").getChild("filter");
        assertNotNull(filter);
        // Note: "resourceType", not "sling:resourceType" - matches upstream ACS Commons exactly (it uses
        // SlingConstants.PROPERTY_RESOURCE_TYPE here, which resolves to the bare, non-namespaced property name).
        // ResourceCollectionUtil doesn't read this property, so it has no effect on getPaths()'s actual behavior.
        assertEquals("cq/workflow/components/collection/definition/resourcelist",
                filter.getValueMap().get("resourceType", String.class));

        final Resource resource0 = filter.getChild("resource_0");
        assertEquals("/content/foo", resource0.getValueMap().get("root", String.class));
        assertEquals("cq/workflow/components/collection/definition/resource",
                resource0.getValueMap().get("resourceType", String.class));
        assertEquals(List.of("include:/content/foo", "include:/content/foo/jcr:content(/.*)?"),
                List.of(resource0.getValueMap().get("rules", String[].class)));

        final Resource resource1 = filter.getChild("resource_1");
        assertEquals("/content/bar", resource1.getValueMap().get("root", String.class));
    }

    @Test
    void create_withoutBucketSegment_usesRootBucketPath() throws Exception {
        seedModernBucketRoot();

        final Page page = underTest.create(context.resourceResolver(), "my-package", new String[]{"/content/foo"});

        assertTrue(page.getPath().startsWith("/var/workflow/packages/"));
        assertFalse(page.getPath().contains("/var/workflow/packages//"));
    }

    @Test
    void isWorkflowPackage_trueForCreatedPackage() throws Exception {
        seedModernBucketRoot();

        final Page page = underTest.create(context.resourceResolver(), "my-package", new String[]{"/content/foo"});

        assertTrue(underTest.isWorkflowPackage(context.resourceResolver(), page.getPath()));
    }

    @Test
    void isWorkflowPackage_falseForOrdinaryPage() {
        context.create().page("/content/some-page");

        assertFalse(underTest.isWorkflowPackage(context.resourceResolver(), "/content/some-page"));
    }

    @Test
    void isWorkflowPackage_falseForNonExistentPath() {
        assertFalse(underTest.isWorkflowPackage(context.resourceResolver(), "/content/does-not-exist"));
    }

    @Test
    void getPaths_returnsEmptyListForNonExistentResource() throws Exception {
        assertEquals(List.of(), underTest.getPaths(context.resourceResolver(), "/content/does-not-exist"));
    }

    @Test
    void getPaths_returnsGivenPathWhenNotAWorkflowPackage() throws Exception {
        context.create().page("/content/some-page");

        assertEquals(List.of("/content/some-page"), underTest.getPaths(context.resourceResolver(), "/content/some-page"));
    }

    @Test
    void getPaths_delegatesToResourceCollectionForAWorkflowPackage() throws Exception {
        final Page page = underTest.create(context.resourceResolver(), "my-package", new String[]{"/content/foo"});

        final WorkflowPackageManagerImpl spied = spy(underTest);
        final ResourceCollection resourceCollection = mock(ResourceCollection.class);
        final Node memberNode = mock(Node.class);
        when(memberNode.getPath()).thenReturn("/content/foo");
        when(resourceCollection.list(any())).thenReturn(List.of(memberNode));
        doReturn(resourceCollection).when(spied).getResourceCollection(any());

        assertEquals(List.of("/content/foo"), spied.getPaths(context.resourceResolver(), page.getPath()));
    }

    @Test
    void delete_removesTheNode() throws Exception {
        final Page page = underTest.create(context.resourceResolver(), "my-package", new String[]{"/content/foo"});
        final String path = page.getPath();

        underTest.delete(context.resourceResolver(), path);

        assertNull(context.resourceResolver().getResource(path));
    }

    @Test
    void delete_noOpForNonExistentPath() {
        assertDoesNotThrow(() -> underTest.delete(context.resourceResolver(), "/content/does-not-exist"));
    }
}
