package com.adobe.acs.workflow;

import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import com.day.cq.workflow.collection.ResourceCollection;
import com.day.cq.workflow.collection.ResourceCollectionManager;
import com.day.cq.workflow.collection.ResourceCollectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component(service = WorkflowPackageManager.class)
public class WorkflowPackageManagerImpl implements WorkflowPackageManager {

    private static final String WORKFLOW_PACKAGES_PATH = "/var/workflow/packages";
    private static final String LEGACY_WORKFLOW_PACKAGES_PATH = "/etc/workflow/packages";
    private static final String WORKFLOW_PACKAGE_TEMPLATE = "/libs/cq/workflow/templates/collectionpage";

    private static final String NT_VLT_DEFINITION = "vlt:PackageDefinition";
    private static final String NN_VLT_DEFINITION = "vlt:definition";
    private static final String NT_SLING_FOLDER = "sling:Folder";
    // Note: despite the name, SlingConstants.PROPERTY_RESOURCE_TYPE resolves to the bare "resourceType" (used for
    // OSGi resource-event properties), not the JCR property "sling:resourceType" - kept here only because
    // upstream ACS Commons sets this same (functionally inert; ResourceCollectionUtil doesn't read it) property
    // on the filter/resource_N definition nodes below. The page's own resourceType (which isResourceType() below
    // actually depends on) is set via the real property name instead.
    private static final String SLING_RESOURCE_TYPE = SlingConstants.PROPERTY_RESOURCE_TYPE;
    private static final String SLING_RESOURCE_TYPE_PROPERTY = "sling:resourceType";

    private static final String FILTER_RESOURCE_TYPE = "cq/workflow/components/collection/definition/resourcelist";
    private static final String FILTER_RESOURCE_RESOURCE_TYPE = "cq/workflow/components/collection/definition/resource";
    private static final String WORKFLOW_PAGE_RESOURCE_TYPE = "cq/workflow/components/collection/page";

    private static final String[] DEFAULT_WORKFLOW_PACKAGE_TYPES = {"cq:Page", "cq:PageContent", "dam:Asset"};

    @Reference
    private ResourceCollectionManager resourceCollectionManager;

    @Override
    public Page create(final ResourceResolver resourceResolver, final String name, final String... paths) throws WCMException, RepositoryException {
        return create(resourceResolver, null, name, paths);
    }

    @Override
    public Page create(final ResourceResolver resourceResolver, final String bucketSegment, final String name,
                        final String... paths) throws WCMException, RepositoryException {
        final Session session = resourceResolver.adaptTo(Session.class);
        final PageManager pageManager = resourceResolver.adaptTo(PageManager.class);

        String workflowPackagePath = getBucketPath(resourceResolver);
        if (StringUtils.isNotBlank(bucketSegment)) {
            workflowPackagePath += "/" + bucketSegment;
        }

        final Node shardNode = JcrUtils.getOrCreateByPath(workflowPackagePath, NT_SLING_FOLDER, NT_SLING_FOLDER, session, false);
        final Page page = pageManager.create(shardNode.getPath(), JcrUtil.createValidName(name), WORKFLOW_PACKAGE_TEMPLATE, name, false);
        final Resource contentResource = page.getContentResource();

        Node node = JcrUtil.createPath(contentResource.getPath() + "/" + NN_VLT_DEFINITION, NT_VLT_DEFINITION, session);
        node = JcrUtil.createPath(node.getPath() + "/filter", JcrConstants.NT_UNSTRUCTURED, session);
        JcrUtil.setProperty(node, SLING_RESOURCE_TYPE, FILTER_RESOURCE_TYPE);

        int i = 0;
        for (final String path : paths) {
            if (path != null) {
                final Node resourceNode = JcrUtil.createPath(node.getPath() + "/resource_" + i++, JcrConstants.NT_UNSTRUCTURED, session);
                JcrUtil.setProperty(resourceNode, "root", path);
                JcrUtil.setProperty(resourceNode, "rules", getIncludeRules(path));
                JcrUtil.setProperty(resourceNode, SLING_RESOURCE_TYPE, FILTER_RESOURCE_RESOURCE_TYPE);
            }
        }

        // Set explicitly rather than relying on the collectionpage template's own content to carry it - keeps
        // isWorkflowPackage() deterministic regardless of whether that /libs template is present/unmodified.
        JcrUtil.setProperty(session.getNode(contentResource.getPath()), SLING_RESOURCE_TYPE_PROPERTY, WORKFLOW_PAGE_RESOURCE_TYPE);

        session.save();
        return page;
    }

    @Override
    public List<String> getPaths(final ResourceResolver resourceResolver, final String path) throws RepositoryException {
        return getPaths(resourceResolver, path, DEFAULT_WORKFLOW_PACKAGE_TYPES);
    }

    @Override
    public List<String> getPaths(final ResourceResolver resourceResolver, final String path, final String[] nodeTypes) throws RepositoryException {
        final Resource resource = resourceResolver.getResource(path);

        if (resource == null) {
            log.warn("Requesting paths for a non-existent resource [ {} ]; returning empty results.", path);
            return Collections.emptyList();
        }

        if (!isWorkflowPackage(resourceResolver, path)) {
            log.debug("Requesting paths for a non-Workflow-Package [ {} ]; returning the provided path.", path);
            return List.of(path);
        }

        final PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
        final Page page = pageManager.getContainingPage(path);

        if (page != null && page.getContentResource() != null) {
            final Node node = page.getContentResource().adaptTo(Node.class);
            final ResourceCollection resourceCollection = getResourceCollection(node);

            if (resourceCollection != null) {
                final List<String> collectionPaths = new ArrayList<>();
                for (final Node member : resourceCollection.list(nodeTypes)) {
                    collectionPaths.add(member.getPath());
                }
                return collectionPaths;
            }
        }

        return List.of(path);
    }

    /**
     * Package-private so tests can stub it directly instead of needing a working
     * {@link ResourceCollectionManager} registration.
     */
    ResourceCollection getResourceCollection(final Node node) throws RepositoryException {
        return ResourceCollectionUtil.getResourceCollection(node, resourceCollectionManager);
    }

    @Override
    public void delete(final ResourceResolver resourceResolver, final String path) throws RepositoryException {
        final Resource resource = resourceResolver.getResource(path);
        if (resource == null) {
            log.error("Requesting to delete a non-existent Workflow Package [ {} ]", path);
            return;
        }

        final Node node = resource.adaptTo(Node.class);
        if (node == null) {
            log.error("Trying to delete a Workflow Package resource [ {} ] that does not resolve to a Node.", resource.getPath());
            return;
        }

        node.remove();
        node.getSession().save();
    }

    @Override
    public boolean isWorkflowPackage(final ResourceResolver resourceResolver, final String path) {
        final PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
        final Page page = pageManager.getPage(path);
        if (page == null) {
            return false;
        }

        final Resource contentResource = page.getContentResource();
        if (contentResource == null || !contentResource.isResourceType(WORKFLOW_PAGE_RESOURCE_TYPE)) {
            return false;
        }

        return contentResource.getChild(NN_VLT_DEFINITION) != null;
    }

    private static String[] getIncludeRules(final String path) {
        return new String[]{
                "include:" + path,
                "include:" + path + "/jcr:content(/.*)?"
        };
    }

    /**
     * Resolved from the caller's own resolver on every call rather than cached at OSGi activation time, so this
     * service needs no dedicated service user just to probe which root exists.
     */
    private static String getBucketPath(final ResourceResolver resourceResolver) {
        return resourceResolver.getResource(WORKFLOW_PACKAGES_PATH) != null ? WORKFLOW_PACKAGES_PATH : LEGACY_WORKFLOW_PACKAGES_PATH;
    }
}
