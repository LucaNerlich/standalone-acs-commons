<%@ include file="/libs/granite/ui/global.jsp" %>
<%
%>
<%@ page session="false"
%>
<%@ page import="com.adobe.acs.include.ParameterizedResourceWrapper" %>
<%@ page import="com.adobe.granite.ui.components.Config" %>
<%@ page import="org.apache.sling.api.resource.Resource" %>
<%

    final Config cfg = cmp.getConfig();

    final String path = cfg.get("path", String.class);

    if (path == null || path.isEmpty()) {
        return;
    }

    // Get the resource using resourceResolver so that the search path (/apps, /libs) is applied,
    // matching how "path" is authored elsewhere (e.g. "myapp/widgets/headline/headline").
    final Resource targetResource = resourceResolver.getResource(path);

    if (targetResource == null) {
        return;
    }

    final Resource parametersResource = resource.getChild("parameters");

    // Opt-in namespace cascading: combines this include's own "namespace" attribute (if any) with an
    // ancestor include's namespace (if this include is itself nested inside an already-namespaced snippet).
    // Existing includes with no "namespace" attribute and no ancestor namespace are unaffected.
    final String namespace = ParameterizedResourceWrapper.cascadeNamespace(resource, cfg.get("namespace", ""));

    final Resource wrapped = ParameterizedResourceWrapper.wrap(targetResource, parametersResource, namespace);

    cmp.include(wrapped, cfg.get("resourceType", String.class), cmp.getOptions());
%>
