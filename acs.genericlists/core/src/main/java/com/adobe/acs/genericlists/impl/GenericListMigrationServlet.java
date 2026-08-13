package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.api.GenericListMigrationReport;
import com.adobe.acs.genericlists.api.GenericListMigrationService;
import com.adobe.acs.genericlists.api.GenericListValidationIssue;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;

import javax.servlet.Servlet;
import java.io.IOException;

/**
 * Explicit operational entry point for dry-run-first migration.
 *
 * <p>POST {@code /bin/acs-genericlists/migrate} with {@code path}, {@code targetPath}, optional
 * {@code overwrite}, and optional {@code dryRun} (default {@code true}). ACLs on the source/target paths determine
 * access; deployments should grant this endpoint only to trusted author administrators.</p>
 */
@Component(service = Servlet.class, property = "sling.servlet.methods=" + HttpConstants.METHOD_POST)
@SlingServletPaths("/bin/acs-genericlists/migrate")
@ServiceDescription("Generic List Migration Servlet")
public final class GenericListMigrationServlet extends SlingAllMethodsServlet {

    @Reference
    private GenericListMigrationService migrationService;

    @Override
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException {
        final String sourcePath = request.getParameter("path");
        final String targetPath = request.getParameter("targetPath");
        if (sourcePath == null || sourcePath.isBlank() || targetPath == null || targetPath.isBlank()) {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "path and targetPath are required.");
            return;
        }

        final Resource source = request.getResourceResolver().getResource(sourcePath);
        if (source == null) {
            response.setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
            writeError(response, "Source resource is not readable.");
            return;
        }

        final boolean dryRun = parseBoolean(request.getParameter("dryRun"), true);
        final boolean overwrite = parseBoolean(request.getParameter("overwrite"), false);
        final GenericListMigrationReport report = migrationService.migrate(source, targetPath, overwrite, dryRun);
        response.setStatus(report.isMigrated() || report.isDryRun()
                ? SlingHttpServletResponse.SC_OK
                : SlingHttpServletResponse.SC_CONFLICT);
        writeReport(response, report);
    }

    private static boolean parseBoolean(final String value, final boolean defaultValue) {
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private static void writeError(final SlingHttpServletResponse response, final String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + escape(message) + "\"}");
    }

    private static void writeReport(final SlingHttpServletResponse response, final GenericListMigrationReport report)
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        final JSONWriter json = new JSONWriter(response.getWriter());
        try {
            json.object()
                    .key("sourcePath").value(report.getSourcePath())
                    .key("targetPath").value(report.getTargetPath())
                    .key("dryRun").value(report.isDryRun())
                    .key("migrated").value(report.isMigrated())
                    .key("messages").array();
            for (final String message : report.getMessages()) {
                json.value(message);
            }
            json.endArray().key("validationIssues").array();
            for (final GenericListValidationIssue issue : report.getValidationIssues()) {
                json.object()
                        .key("path").value(issue.getResourcePath())
                        .key("code").value(issue.getCode())
                        .key("message").value(issue.getMessage())
                        .endObject();
            }
            json.endArray().endObject();
        } catch (JSONException ex) {
            throw new IOException("Unable to serialize migration report", ex);
        }
    }

    private static String escape(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
