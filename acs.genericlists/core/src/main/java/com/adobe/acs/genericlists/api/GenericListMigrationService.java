package com.adobe.acs.genericlists.api;

import org.apache.sling.api.resource.Resource;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Explicit, idempotent migration service for legacy in-house and ACS Commons Generic List content.
 *
 * <p>Callers should always run with {@code dryRun=true} first. The service never runs automatically on activation
 * or package installation.</p>
 */
@ProviderType
public interface GenericListMigrationService {

    /**
     * Migrates a source list to a canonical list resource, or migrates a legacy page in place when the target path
     * matches that page's path. Invalid rows are reported and not copied.
     *
     * @param source source resource or list page
     * @param targetPath canonical target resource path, or the source page path for an in-place page upgrade
     * @param overwrite whether an existing standalone target may be replaced
     * @param dryRun true to inspect without changing repository content
     * @return a machine-readable migration report
     */
    GenericListMigrationReport migrate(Resource source, String targetPath, boolean overwrite, boolean dryRun);
}
