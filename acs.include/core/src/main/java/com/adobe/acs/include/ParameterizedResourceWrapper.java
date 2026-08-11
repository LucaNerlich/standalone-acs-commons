package com.adobe.acs.include;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Wraps a resource (and, recursively, its whole subtree) so that {@code ${{key:default}}} placeholders in
 * String properties are substituted with values from a flat parameters map (see {@link Placeholder}), falling
 * back to the literal default (or an empty string) when a key isn't supplied.
 * <p>
 * Two additional, opt-in capabilities mirror ACS Commons' "Parameterized Include for Dialog Widgets":
 * <ul>
 *     <li>namespace cascading ({@link #wrap(Resource, Resource, String)}, {@link #cascadeNamespace}) - prefixes
 *     a fixed set of property names so the same snippet can be included multiple times without colliding;</li>
 *     <li>conditional visibility - a resolved {@code hide} property excludes a (sub)resource from
 *     {@link #getChild(String)} / {@link #listChildren()}.</li>
 * </ul>
 * Typed placeholder casting ({@code ${{(Boolean|Long|Double)key:default}}}) is handled by {@link Placeholder}.
 * None of this is active unless a dialog opts in via the {@code namespace} attribute, a {@code hide}
 * property, or a {@code (Type)} placeholder prefix - existing dialogs that only use the plain
 * {@code ${{key:default}}} form are unaffected.
 * https://adobe-consulting-services.github.io/acs-aem-commons/features/parameterized-namespace-include/index.html
 */
public final class ParameterizedResourceWrapper extends ResourceWrapper {

    // Used via apps/acs-include/granite/ui/components/include/include.jsp

    private static final String PN_HIDE = "hide";
    private static final String PN_RESOURCE_TYPE = "sling:resourceType";

    /**
     * Property names namespace cascading prefixes, matching ACS Commons' defaults.
     */
    private static final Set<String> NAMESPACED_PROPERTIES =
            Set.of("name", "fileNameParameter", "fileReferenceParameter");

    /**
     * Resource types whose subtree namespace cascading does not propagate into, matching ACS Commons'
     * default {@code resourceTypesIgnoreChildren} - a multifield's own per-row indexing already guarantees
     * uniqueness, so renamespacing its fields would only break it.
     */
    private static final Set<String> NAMESPACE_BOUNDARY_RESOURCE_TYPES =
            Collections.singleton("granite/ui/components/coral/foundation/form/multifield");

    private final Map<String, String> parameters;
    private final String namespace;

    public ParameterizedResourceWrapper(final Resource resource, final Map<String, String> parameters,
                                        final String namespace) {
        super(resource);
        this.parameters = parameters;
        this.namespace = namespace == null ? "" : namespace;
    }

    /**
     * @param target             the resource to wrap (e.g. the resource resolved from an include's {@code path})
     * @param parametersResource the include's {@code parameters} child resource, or {@code null} if absent
     * @return {@code target} wrapped so its subtree's placeholders are substituted from {@code parametersResource}'s
     * own (non-{@code jcr:}) String properties
     */
    public static ParameterizedResourceWrapper wrap(final Resource target, final Resource parametersResource) {
        return wrap(target, parametersResource, null);
    }

    /**
     * @param target             the resource to wrap (e.g. the resource resolved from an include's {@code path})
     * @param parametersResource the include's {@code parameters} child resource, or {@code null} if absent
     * @param namespace          the active namespace (see {@link #cascadeNamespace}), or {@code null}/empty if namespacing
     *                           is not in use
     * @return {@code target} wrapped so its subtree's placeholders are substituted from {@code parametersResource}'s
     * own (non-{@code jcr:}) String properties, and (if {@code namespace} is non-empty) {@link #NAMESPACED_PROPERTIES}
     * are prefixed with it
     */
    public static ParameterizedResourceWrapper wrap(final Resource target, final Resource parametersResource,
                                                    final String namespace) {
        return new ParameterizedResourceWrapper(target, toParameterMap(parametersResource), namespace);
    }

    /**
     * Combines an ancestor include's ambient namespace (if this include is itself nested inside an
     * already-namespaced snippet) with this include's own {@code namespace} attribute, matching ACS Commons'
     * cascading semantics.
     *
     * @param currentResource the resource currently being rendered (e.g. include.jsp's bound {@code resource}) -
     *                        only resources this class itself produced (via recursion) carry an ambient namespace
     * @param ownNamespace    this include's own {@code namespace} attribute, or {@code null}/empty if absent
     * @return {@code ownNamespace} combined with the ambient namespace as {@code ambient/own}, whichever of the
     * two is present, or empty if neither is
     */
    public static String cascadeNamespace(final Resource currentResource, final String ownNamespace) {
        final String ambient = currentResource instanceof ParameterizedResourceWrapper
                ? ((ParameterizedResourceWrapper) currentResource).namespace : "";
        if (ambient.isEmpty()) {
            return ownNamespace;
        }
        return (ownNamespace == null || ownNamespace.isEmpty()) ? ambient : ambient + "/" + ownNamespace;
    }

    private static Map<String, String> toParameterMap(final Resource parametersResource) {
        final Map<String, String> parameters = new HashMap<>();
        if (parametersResource != null) {
            for (final Map.Entry<String, Object> entry : parametersResource.getValueMap().entrySet()) {
                if (!entry.getKey().startsWith("jcr:") && entry.getValue() instanceof String) {
                    parameters.put(entry.getKey(), (String) entry.getValue());
                }
            }
        }
        return parameters;
    }

    private static boolean isNamespaceBoundary(final Resource resource) {
        return NAMESPACE_BOUNDARY_RESOURCE_TYPES.contains(resource.getValueMap().get(PN_RESOURCE_TYPE, ""));
    }

    private static boolean isHidden(final Resource wrapped) {
        return wrapped.getValueMap().get(PN_HIDE, Boolean.FALSE);
    }

    @Override
    public ValueMap getValueMap() {
        final ValueMap original = getResource().getValueMap();
        final Map<String, Object> substituted = new HashMap<>();
        for (final Map.Entry<String, Object> entry : original.entrySet()) {
            final Object value = entry.getValue();
            if (!(value instanceof String)) {
                substituted.put(entry.getKey(), value);
                continue;
            }
            String resolved = Placeholder.resolve((String) value, parameters);
            if (!namespace.isEmpty() && NAMESPACED_PROPERTIES.contains(entry.getKey())) {
                resolved = applyNamespace(resolved);
            }
            substituted.put(entry.getKey(), resolved);
        }
        return new ValueMapDecorator(substituted);
    }

    @Override
    public <AdapterType> AdapterType adaptTo(final Class<AdapterType> type) {
        if (type == ValueMap.class) {
            return type.cast(getValueMap());
        }
        return super.adaptTo(type);
    }

    @Override
    public Resource getChild(final String relPath) {
        final Resource child = getResource().getChild(relPath);
        if (child == null) {
            return null;
        }
        final ParameterizedResourceWrapper wrapped = wrapChild(child);
        return isHidden(wrapped) ? null : wrapped;
    }

    @Override
    public Iterator<Resource> listChildren() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(getResource().listChildren(), 0), false)
                .map(this::wrapChild)
                .filter(child -> !isHidden(child))
                .map(Resource.class::cast)
                .iterator();
    }

    @Override
    public Iterable<Resource> getChildren() {
        return this::listChildren;
    }

    private ParameterizedResourceWrapper wrapChild(final Resource child) {
        final String childNamespace = isNamespaceBoundary(child) ? "" : namespace;
        return new ParameterizedResourceWrapper(child, parameters, childNamespace);
    }

    private String applyNamespace(final String value) {
        final int dotSlash = value.indexOf("./");
        if (dotSlash >= 0) {
            return "./" + namespace + "/" + value.substring(dotSlash + 2);
        }
        return namespace + "/" + value;
    }
}
