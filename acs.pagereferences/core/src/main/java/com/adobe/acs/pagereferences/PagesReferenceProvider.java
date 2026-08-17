package com.adobe.acs.pagereferences;

import com.day.cq.commons.PathInfo;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.reference.Reference;
import com.day.cq.wcm.api.reference.ReferenceProvider;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reference provider that finds pages referenced (by path, in any string property) inside a given page resource -
 * a slim replacement for ACS AEM Commons' {@code PagesReferenceProvider}, built directly on the native AEM
 * {@link ReferenceProvider} API.
 */
@Designate(ocd = PagesReferenceProvider.Config.class)
@Component(service = ReferenceProvider.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
public final class PagesReferenceProvider implements ReferenceProvider {

    private static final String TYPE_PAGE = "page";
    static final String DEFAULT_PAGE_ROOT_PATH = "/content/";

    @ObjectClassDefinition(name = "ACS Page References - Pages Reference Provider",
            description = "Reference provider that finds pages referenced inside a given page resource.")
    public @interface Config {
        @AttributeDefinition(name = "Page root path", description = "Page root path")
        String page_root_path() default DEFAULT_PAGE_ROOT_PATH;
    }

    private String pageRootPath = DEFAULT_PAGE_ROOT_PATH;

    // any text containing the page root path
    private Pattern pattern = compilePattern(DEFAULT_PAGE_ROOT_PATH);

    @Activate
    void activate(final Config config) {
        final String configuredRootPath = config.page_root_path();
        pageRootPath = configuredRootPath == null || configuredRootPath.isBlank()
                ? DEFAULT_PAGE_ROOT_PATH : configuredRootPath;
        pattern = compilePattern(pageRootPath);
    }

    private static Pattern compilePattern(final String rootPath) {
        return Pattern.compile("([\"']|^)(" + Pattern.quote(rootPath) + ")(\\S|$)");
    }

    @Override
    public List<Reference> findReferences(final Resource resource) {
        final List<Reference> references = new ArrayList<>();

        final ResourceResolver resolver = resource.getResourceResolver();
        final PageManager pageManager = resolver.adaptTo(PageManager.class);

        final Set<Page> pages = new HashSet<>();
        search(resource, pages, pageManager);

        for (final Page page : pages) {
            final Resource contentResource = page.getContentResource();
            if (contentResource != null && !contentResource.getPath().equals(resource.getPath())) {
                references.add(getReference(page));
            }
        }

        return references;
    }

    private void search(final Resource resource, final Set<Page> pages, final PageManager pageManager) {
        findReferencesInResource(resource, pages, pageManager);
        for (final Iterator<Resource> iter = resource.listChildren(); iter.hasNext(); ) {
            search(iter.next(), pages, pageManager);
        }
    }

    private void findReferencesInResource(final Resource resource, final Set<Page> pages, final PageManager pageManager) {
        final ValueMap map = resource.getValueMap();
        for (final Object value : map.values()) {
            if (value instanceof String) {
                addPagesFromPropertyValue((String) value, pages, pageManager);
            } else if (value instanceof String[]) {
                for (final String strValue : (String[]) value) {
                    addPagesFromPropertyValue(strValue, pages, pageManager);
                }
            }
        }
    }

    private void addPagesFromPropertyValue(final String strValue, final Set<Page> pages, final PageManager pageManager) {
        if (pattern.matcher(strValue).find()) {
            for (final String path : getAllPathsInAProperty(strValue)) {
                final Page page = pageManager.getContainingPage(path);
                if (page != null) {
                    pages.add(page);
                }
            }
        }
    }

    private Reference getReference(final Page page) {
        return new Reference(TYPE_PAGE,
                String.format("%s (Page)", page.getName()),
                page.getContentResource().getParent(),
                getLastModifiedTimeOfResource(page));
    }

    private long getLastModifiedTimeOfResource(final Page page) {
        final var mod = page.getLastModified();
        return mod != null ? mod.getTimeInMillis() : -1;
    }

    private Set<String> getAllPathsInAProperty(final String value) {
        if (isSinglePathInValue(value)) {
            return getSinglePath(value);
        } else {
            return getMultiplePaths(value);
        }
    }

    private boolean isSinglePathInValue(final String value) {
        return value.startsWith("/");
    }

    private Set<String> getSinglePath(final String value) {
        final Set<String> paths = new HashSet<>();
        paths.add(decode(value));
        return paths;
    }

    private Set<String> getMultiplePaths(final String value) {
        final Set<String> paths = new HashSet<>();
        int startPos = value.indexOf(pageRootPath, 1);
        while (startPos != -1) {
            final char charBeforeStartPos = value.charAt(startPos - 1);
            if (charBeforeStartPos == '\'' || charBeforeStartPos == '"') {
                final int endPos = value.indexOf(charBeforeStartPos, startPos);
                if (endPos > startPos) {
                    final String ref = value.substring(startPos, endPos);
                    paths.add(decode(ref));
                    startPos = endPos;
                }
            }
            startPos = value.indexOf(pageRootPath, startPos + 1);
        }
        return paths;
    }

    private String decode(final String url) {
        return new PathInfo(url).getResourcePath();
    }
}
