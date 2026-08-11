# acs.genericlists

In-house replacement for ACS AEM Commons' "Generic Lists" feature. Lets an author maintain a page of ordered
title/value pairs (e.g. a shared dropdown-options list) through a normal touch UI dialog, and lets any other code
read that list back via `Page#adaptTo(GenericList.class)`.

## Modules

- `core` - `com.adobe.acs.genericlists.GenericList`/`GenericListImpl`/`GenericListAdapterFactory`: adapts a
  `com.day.cq.wcm.api.Page` (whose content resource is `acs-genericlists/components/utilities/genericlist`) to
  `GenericList`, giving `getItems()` and `lookupTitle(value[, locale])`.
- `ui.apps` / `ui.apps.structure` - the `genericlist` component (dialog only, no render script - see below), a
  `genericlist` page template restricted to `/content/generic-lists(/.*)?`, and a `datasource` JSP that feeds a
  Granite UI `select`/`radiogroup`'s options from an existing generic-list page.

## How it works

A "generic list" is a `cq:Page` (template `genericlist`, resource type
`acs-genericlists/components/utilities/genericlist`) whose dialog is a composite `multifield` writing one child
node per row under `./list/`, each with a `jcr:title` and `value` property - the exact storage shape ACS Commons
used, just authored through a modern touch UI dialog instead of its MCP-generated classic-UI form.

```java
@Reference
private ResourceResolverFactory resourceResolverFactory; // or however you get a resolver + PageManager

Page listPage = pageManager.getPage("/content/generic-lists/countries");
GenericList list = listPage.adaptTo(GenericList.class);

for (GenericList.Item item : list.getItems()) {
    // item.getTitle(), item.getTitle(locale), item.getValue()
}

String title = list.lookupTitle("de"); // e.g. "Germany"
```

Feeding a dialog field's options from a list page:

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

## Building & deploying

```bash
mvn clean install                                   # build all modules
mvn clean install -PautoInstallBundle               # build + deploy the core bundle (default: localhost:4502)
mvn clean install -PautoInstallPackage              # build + deploy the ui.apps/ui.apps.structure content packages
```

Override `aem.host`/`aem.port` (and `vault.user`/`vault.password` if not `admin`/`admin`) via `-D` if your instance
differs from the defaults.

## Not ported

- **The MCP-generated classic-UI dialog and its `/generic-lists.html` bulk-properties admin console.** Upstream
  built its dialog dynamically at runtime from `@FormField`-annotated interface methods
  (`com.adobe.acs.commons.mcp.form.GeneratedDialogWrapper`), an entire separate framework this module has no
  dependency on. A plain, static Granite UI touch-UI dialog (`_cq_dialog`) achieves the identical storage contract
  (child nodes with `jcr:title`/`value` under `list`) with far less machinery, at the cost of the dialog being
  fixed markup instead of reflectively generated - a non-issue since the shape of a title/value pair never changes.
- **The legacy `list.json.jsp` JSON export**, which used the deprecated `org.apache.sling.commons.json` API. The
  `datasource` JSP (for feeding dialog field options) and the `GenericList` Java API (for any other code) cover the
  same use cases without that dependency.
- **A page render script.** A generic-list page is a pure data container, edited entirely through its dialog; there
  is nothing meaningful to preview, so (unlike upstream's `body.jsp`, itself just an inline copy of the same
  dialog-generation form) no render script is provided at all.
- **The `allowedPaths` root.** Upstream defaulted to `/etc/acs-commons/lists`; this module defaults to
  `/content/generic-lists(/.*)?` instead, since `/etc` is deprecated for custom content in AEM as a Cloud Service.
  Adjust `allowedPaths` in
  [`templates/genericlist/.content.xml`](ui.apps/src/main/content/jcr_root/apps/acs-genericlists/templates/genericlist/.content.xml)
  if you want a different root.
