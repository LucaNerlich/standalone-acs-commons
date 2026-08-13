package com.adobe.acs.genericlists.api;

import org.osgi.annotation.versioning.ProviderType;

import java.util.Objects;

/**
 * A non-fatal schema diagnostic discovered while reading a Generic List resource.
 */
@ProviderType
public final class GenericListValidationIssue {

    private final String resourcePath;
    private final String code;
    private final String message;

    public GenericListValidationIssue(final String resourcePath, final String code, final String message) {
        this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath");
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return code + " at " + resourcePath + ": " + message;
    }
}
