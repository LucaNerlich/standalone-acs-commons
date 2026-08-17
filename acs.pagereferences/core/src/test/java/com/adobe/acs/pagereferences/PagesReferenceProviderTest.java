package com.adobe.acs.pagereferences;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.reference.Reference;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Calendar;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class PagesReferenceProviderTest {

    private final AemContext context = new AemContext();

    private final PagesReferenceProvider underTest = new PagesReferenceProvider();

    private final PageManager pageManager = mock(PageManager.class);

    @BeforeEach
    void setUp() {
        final PagesReferenceProvider.Config config = mock(PagesReferenceProvider.Config.class);
        when(config.page_root_path()).thenReturn(PagesReferenceProvider.DEFAULT_PAGE_ROOT_PATH);
        underTest.activate(config);

        context.registerAdapter(ResourceResolver.class, PageManager.class, (Function<ResourceResolver, PageManager>) resolver -> pageManager);

        context.load().json(getClass().getResourceAsStream("PagesReferenceProviderTest.json"), "/content/geometrixx");

        registerPage("/content/geometrixx/en", "geometrixx");
        registerPage("/content/geometrixx/en/toolbar", "geometrixx1");
        registerPage("/content/geometrixx/reftoself", "to self");
    }

    private void registerPage(final String path, final String name) {
        final Page result = mock(Page.class, path);
        when(pageManager.getContainingPage(path)).thenReturn(result);
        when(result.getName()).thenReturn(name);
        when(result.getLastModified()).thenReturn(Calendar.getInstance());
        when(result.getContentResource()).then(i -> context.resourceResolver().getResource(path + "/jcr:content"));
    }

    @Test
    void testSingleReferenceToaPage() {
        final List<Reference> actual = underTest.findReferences(context.resourceResolver().getResource("/content/geometrixx/oneref/jcr:content"));
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("geometrixx (Page)", actual.get(0).getName());
    }

    @Test
    void testNoReferenceToAnyPage() {
        final List<Reference> actual = underTest.findReferences(context.resourceResolver().getResource("/content/geometrixx/noref/jcr:content"));
        assertNotNull(actual);
        assertEquals(0, actual.size());
    }

    @Test
    void testReferenceToMissingPage() {
        final List<Reference> actual = underTest.findReferences(context.resourceResolver().getResource("/content/geometrixx/badref/jcr:content"));
        assertNotNull(actual);
        assertEquals(0, actual.size());
    }

    @Test
    void testSingleReferenceToManyPages() {
        final List<Reference> actual = underTest.findReferences(context.resourceResolver().getResource("/content/geometrixx/tworefs/jcr:content"));
        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals(List.of("geometrixx (Page)", "geometrixx1 (Page)"), toSortedNames(actual));
    }

    @Test
    void testReferenceOnChildNode() {
        final List<Reference> actual = underTest.findReferences(context.resourceResolver().getResource("/content/geometrixx/tworefsChild/jcr:content"));
        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals(List.of("geometrixx (Page)", "geometrixx1 (Page)"), toSortedNames(actual));
    }

    @Test
    void testManyReferenceToSinglePages() {
        final List<Reference> actual = underTest.findReferences(context.resourceResolver().getResource("/content/geometrixx/tworefsSamePage/jcr:content"));
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("geometrixx (Page)", actual.get(0).getName());
    }

    @Test
    void testMultiValuedProp() {
        final List<Reference> actual = underTest.findReferences(context.resourceResolver().getResource("/content/geometrixx/mvRef/jcr:content"));
        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals(List.of("geometrixx (Page)", "geometrixx1 (Page)"), toSortedNames(actual));
    }

    @Test
    void testMultiValuedPropWithOther() {
        final List<Reference> actual = underTest.findReferences(context.resourceResolver().getResource("/content/geometrixx/mvRefSamePage/jcr:content"));
        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals(List.of("geometrixx (Page)", "geometrixx1 (Page)"), toSortedNames(actual));
    }

    @Test
    void testMultipleReferencesReferenceToPages() {
        final List<Reference> actual = underTest.findReferences(context.resourceResolver().getResource("/content/geometrixx/commaSeparated/jcr:content"));
        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals(List.of("geometrixx (Page)", "geometrixx1 (Page)"), toSortedNames(actual));
    }

    @Test
    void testRefToSelf() {
        final List<Reference> actual = underTest.findReferences(context.resourceResolver().getResource("/content/geometrixx/reftoself/jcr:content"));
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("geometrixx (Page)", actual.get(0).getName());
    }

    @Test
    void testPageReferenceResourcePath() {
        // The references resource should point to the cq:Page and not the [cq:Page]/jcr:content, per
        // https://github.com/Adobe-Consulting-Services/acs-aem-commons/issues/1283
        final List<Reference> actual = underTest.findReferences(context.resourceResolver().getResource("/content/geometrixx/oneref/jcr:content"));
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("/content/geometrixx/en", actual.get(0).getResource().getPath());
    }

    private List<String> toSortedNames(final List<Reference> actual) {
        return actual.stream().map(Reference::getName).sorted().collect(Collectors.toList());
    }
}
