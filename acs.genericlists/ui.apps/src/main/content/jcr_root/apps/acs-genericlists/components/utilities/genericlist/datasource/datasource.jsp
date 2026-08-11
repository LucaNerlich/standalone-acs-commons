<%--
  In-house replacement for ACS AEM Commons' Generic Lists datasource JSP. Feeds a Granite UI select/radiogroup's
  options from an existing acs-genericlists page: point this widget's own "datasource" child node's "path"
  property at that page.
--%>
<%@page session="false" import="
                  org.apache.sling.api.resource.Resource,
                  org.apache.sling.api.resource.ResourceUtil,
                  org.apache.sling.api.resource.ValueMap,
                  org.apache.sling.api.resource.ResourceResolver,
                  org.apache.sling.api.resource.ResourceMetadata,
                  org.apache.sling.api.wrappers.ValueMapDecorator,
                  java.util.List,
                  java.util.ArrayList,
                  java.util.HashMap,
                  java.util.Locale,
                  com.adobe.acs.genericlists.GenericList,
                  com.adobe.granite.ui.components.ds.DataSource,
                  com.adobe.granite.ui.components.ds.EmptyDataSource,
                  com.adobe.granite.ui.components.ds.SimpleDataSource,
                  com.adobe.granite.ui.components.ds.ValueMapResource,
                  com.day.cq.wcm.api.Page,
                  com.day.cq.wcm.api.PageManager"%><%
%><%@taglib prefix="cq" uri="http://www.day.com/taglibs/cq/1.0" %><%
%><cq:defineObjects/><%

// set fallback
request.setAttribute(DataSource.class.getName(), EmptyDataSource.instance());

Locale locale = request.getLocale();

Resource datasource = resource.getChild("datasource");
ResourceResolver resolver = resource.getResourceResolver();
ValueMap dsProperties = ResourceUtil.getValueMap(datasource);
String genericListPath = dsProperties.get("path", String.class);
if (genericListPath != null) {
    Page genericListPage = pageManager.getPage(genericListPath);
    if (genericListPage != null) {
        GenericList list = genericListPage.adaptTo(GenericList.class);
        if (list != null) {
            List<Resource> fakeResourceList = new ArrayList<Resource>();
            for (GenericList.Item item : list.getItems()) {
                ValueMap vm = new ValueMapDecorator(new HashMap<String, Object>());
                vm.put("value", item.getValue());
                vm.put("text", item.getTitle(locale));

                fakeResourceList.add(new ValueMapResource(resolver, new ResourceMetadata(), "nt:unstructured", vm));
            }

            DataSource ds = new SimpleDataSource(fakeResourceList.iterator());
            request.setAttribute(DataSource.class.getName(), ds);
        }
    }
}
%>
