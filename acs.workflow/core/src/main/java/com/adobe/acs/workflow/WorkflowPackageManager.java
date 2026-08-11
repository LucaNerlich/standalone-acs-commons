package com.adobe.acs.workflow;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.WCMException;
import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.RepositoryException;
import java.util.List;

/**
 * Creates and reads AEM Workflow "packages" - {@code cq:Page}s under {@code /var/workflow/packages} that hold a
 * multi-path payload (e.g. the set of pages a bulk publish/lock/delete workflow acts on), using the same
 * {@code cq/workflow/components/collection/page} structure the Workflow console itself creates.
 */
public interface WorkflowPackageManager {

    /**
     * Creates a Workflow Package for the provided paths, under {@code /var/workflow/packages/<bucketSegment>}.
     *
     * @param resourceResolver the resource resolver used to create the package
     * @param bucketSegment    a path segment used to organize workflow packages; may be {@code null}/blank
     * @param name             the name of the package
     * @param paths            the paths to include
     * @return the Page representing the Workflow Package
     */
    Page create(ResourceResolver resourceResolver, String bucketSegment, String name, String... paths) throws WCMException, RepositoryException;

    /**
     * Same as {@link #create(ResourceResolver, String, String, String...)} with no bucket segment.
     */
    Page create(ResourceResolver resourceResolver, String name, String... paths) throws WCMException, RepositoryException;

    /**
     * Gets the payload paths in the Workflow Package, using the default node types ({@code cq:Page},
     * {@code cq:PageContent}, {@code dam:Asset}).
     * <ul>
     *     <li>If {@code workflowPackagePath} doesn't resolve to a resource: an empty list</li>
     *     <li>If it resolves but isn't a Workflow Package: a list of the one given path</li>
     *     <li>If it is a Workflow Package: the list of all paths contained in it (not the package itself)</li>
     * </ul>
     */
    List<String> getPaths(ResourceResolver resourceResolver, String workflowPackagePath) throws RepositoryException;

    /**
     * Same as {@link #getPaths(ResourceResolver, String)}, with an explicit set of allowed node types.
     */
    List<String> getPaths(ResourceResolver resourceResolver, String workflowPackagePath, String[] nodeTypes) throws RepositoryException;

    /**
     * Deletes the specified Workflow Package. A no-op (logged) if the path doesn't resolve to a resource.
     */
    void delete(ResourceResolver resourceResolver, String workflowPackagePath) throws RepositoryException;

    /**
     * @return {@code true} if {@code path} resolves to a Workflow Package Page
     */
    boolean isWorkflowPackage(ResourceResolver resourceResolver, String path);
}
