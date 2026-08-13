# acs.genericlists

In-house replacement for ACS AEM Commons' Generic Lists feature. Authors maintain an ordered collection of
key/value pairs on a normal AEM page, while consuming code continues to use `Page#adaptTo(GenericList.class)` or
the Granite UI datasource servlet.

## Architecture

- `acs-genericlists/components/page` is a hidden proxy of `core/wcm/components/page/v3/page`. It owns page
  rendering and the inherited Page Properties dialog, but no list-specific authoring behavior.
- `acs-genericlists/components/key-value-list` owns the composite multifield and the list presentation. Its HTL
  renders the authored rows as a Coral UI 3 table and uses HTL's contextual escaping for both columns.
- `/conf/acs-genericlists/settings/wcm/templates/generic-list` is an editable template restricted to
  `/content/generic-lists(/.*)?`. Its structure is `page -> Core Container -> Key/Value List`.
- `GenericListImpl` is both the resource Sling Model for the Key/Value List component and the implementation used
  by `GenericListAdapterFactory` when adapting a page.

The same page/container/component structure is present in the `generic-list-base` template type, so templates
created from that type start from the supported Core Components proxy pattern.

## Authoring and storage

Create a page from the **Generic List** template, open the **Key/Value List** component dialog, and add rows to
the Coral 3 composite multifield. Each row has a required **Key** and **Value** field.

The component persists rows below:

```text
jcr:content/root/keyValueList/items/item0
    jcr:title = "Germany"
    value     = "de"
```

`jcr:title` remains the stored key so the public ACS-compatible API (`Item#getTitle()` and
`lookupTitle(value)`) remains unchanged. The adapter also recognizes the version 1.1.0 resource type and its
legacy `list/item` storage shape.

```java
Page listPage = pageManager.getPage("/content/generic-lists/countries");
GenericList list = listPage.adaptTo(GenericList.class);

for (GenericList.Item item : list.getItems()) {
    // item.getTitle() is the authored key; item.getValue() is its value.
}

String key = list.lookupTitle("de"); // "Germany"
```

The Key/Value List resource itself can also be adapted directly:

```java
GenericList list = keyValueListResource.adaptTo(GenericList.class);
```

## Granite UI datasource

Use an existing list page as the options source for a Coral 3 select or radio group:

```xml
<country
        jcr:primaryType="nt:unstructured"
        sling:resourceType="granite/ui/components/coral/foundation/form/select"
        fieldLabel="Country"
        name="./country">
    <datasource
            jcr:primaryType="nt:unstructured"
            sling:resourceType="acs-genericlists/components/utilities/genericlist/datasource"
            path="/content/generic-lists/countries"/>
</country>
```

## Building and deploying

From this module:

```bash
mvn clean verify
mvn clean install -PautoInstallBundle
mvn clean install -PautoInstallPackage
```

The default target is `localhost:4502`; override the Maven deployment properties for other environments.

## Deliberately not ported

- The ACS Commons MCP-generated Classic UI dialog and `/generic-lists.html` bulk admin console. A static Coral 3
  component dialog supplies the fixed key/value schema without the MCP form-generation dependency.
- The legacy `list.json.jsp` exporter. The datasource servlet and Java API cover the supported consumption paths.
- The upstream `/etc/acs-commons/lists` location. Custom content belongs below `/content`; adjust the template's
  `allowedPaths` if a different site root is required.
