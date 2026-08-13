package com.adobe.acs.genericlists.api;

import org.osgi.annotation.versioning.ProviderType;

import java.util.List;

/** Result of an explicit Generic List migration or dry-run inspection. */
@ProviderType
public final class GenericListMigrationReport {

    private final String sourcePath;
    private final String targetPath;
    private final boolean dryRun;
    private final boolean migrated;
    private final List<String> messages;
    private final List<GenericListValidationIssue> validationIssues;

    public GenericListMigrationReport(
            final String sourcePath,
            final String targetPath,
            final boolean dryRun,
            final boolean migrated,
            final List<String> messages,
            final List<GenericListValidationIssue> validationIssues) {
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.dryRun = dryRun;
        this.migrated = migrated;
        this.messages = List.copyOf(messages);
        this.validationIssues = List.copyOf(validationIssues);
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isMigrated() {
        return migrated;
    }

    public List<String> getMessages() {
        return messages;
    }

    public List<GenericListValidationIssue> getValidationIssues() {
        return validationIssues;
    }
}
