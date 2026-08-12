# acs.genericlists

In-house replacement for ACS AEM Commons' "Generic Lists" feature. Lets an author maintain a page of ordered
title/value pairs (e.g. a shared dropdown-options list) through a normal touch UI dialog, and lets any other code
read that list back via `Page#adaptTo(GenericList.class)`.

## Modules

- `core` - `com.adobe.acs.genericlists.GenericList`/`GenericListImpl`/`GenericListAdapterFactory`: adapts a
  `com.day.cq.wcm.api.Page` (whose content resource is `acs-genericlists/components/utilities/genericlist`) to
  `GenericList`, giving `getItems()` and `lookupTitle(value[, locale])`. Also `GenericListDataSourceServlet`,
  registered against the `genericlist/datasource` resource type, which feeds a Granite UI `select`/`radiogroup`'s
  options from an existing generic-list page.
- `ui.apps` / `ui.apps.structure` - the `genericlist` component: a dialog only, no render script of its own (see
  below).
- `ui.content` - the `generic-list` **Editable Template** (`/conf/acs-genericlists/settings/wcm/templates/generic-list`,
  restricted to `/content/generic-lists(/.*)?`), so authors can create list pages through the normal Sites console
  "Create Page" wizard.

## How it works

A "generic list" is a `cq:Page` (template `/conf/acs-genericlists/settings/wcm/templates/generic-list`, resource type
`acs-genericlists/components/utilities/genericlist`) that renders nothing at all - it's a pure data container,
authored and edited entirely through **Page Properties** (`properties.html?item=...` or the Sites console's
properties action, and the Create Page wizard's properties step). There is nothing to preview on the page itself.

The dialog's composite multifield writes rows under `./list/item`, one child node per row (auto-named by Granite,
e.g. `item0`, `item1`, ...), each with a `jcr:title` and `value` property - the same title/value shape ACS Commons
used, just authored through a modern touch UI dialog instead of its MCP-generated classic-UI form.
`GenericListImpl` reads those same nodes (`page.getContentResource().getChild("list")`, then that resource's `item`
child if present, falling back to its own direct children otherwise - so content authored by hand, e.g. via
repoinit with items placed directly under `list`, keeps working unchanged).

**Storage detail worth knowing**: a composite multifield's `name` attribute is the *exact* path of the
auto-populated collection (Granite doesn't strip its own last segment) - so `name="./list/item"` puts rows at
`list/item/item0`, not `list/item0`.

The page component extends `core/wcm/components/page/v3/page` (`sling:resourceSuperType`) purely so its dialog
inherits the standard "Basic" tab (Title, Name, Tags, etc.) and so the Create Page wizard's properties step renders
correctly - a static dialog with no such inheritance leaves that step empty with "Create" permanently disabled,
since AEM as a Cloud Service's page-creation flow expects it. `sling:hideChildren` on the dialog's tabs then
suppresses everything else that inheritance brings along (Images, Advanced, Cloud Services, Personalization,
Permissions, PWA, Blueprint, Live Copy, Preview URL) - none of it applies to a pure data/config page. Only "Basic"
and our own "List Items" tab show, in Page Properties.

The "List Items" tab is marked `cq:showOnCreate="{Boolean}false"`, so it's hidden entirely in the Create Page
wizard's properties step (only "Basic" shows there). AEM's wizard extracts and renders `cq:showOnCreate` fields in
isolation from the rest of the dialog, and that isolated rendering doesn't reliably produce the template markup a
composite multifield's "Add" button needs - leaving newly-added rows with no visible Title/Value inputs. Simply
*not* marking the multifield `cq:showOnCreate` isn't enough on its own either: the tab still renders as a
navigation item during creation, just empty, since tab visibility and field visibility are gated separately.
Explicitly hiding the whole tab avoids both problems. This project's own `keyvalue-config-page` hits the same
composite-multifield limitation and works around it the same way: mark simple fields `cq:showOnCreate`, never a
composite multifield. List items are fully editable immediately after creation, in Page Properties.

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
  dependency on. ("MCP" here is ACS Commons' own "Manage Controlled Processes" tooling framework, unrelated to
  Anthropic's Model Context Protocol.) A plain, static Granite UI touch-UI dialog (`_cq_dialog`) achieves the identical storage contract
  (child nodes with `jcr:title`/`value` under `list`) with far less machinery, at the cost of the dialog being
  fixed markup instead of reflectively generated - a non-issue since the shape of a title/value pair never changes.
- **The legacy `list.json.jsp` JSON export**, which used the deprecated `org.apache.sling.commons.json` API. The
  `GenericListDataSourceServlet` (for feeding dialog field options) and the `GenericList` Java API (for any other
  code) cover the same use cases without that dependency.
- **A page render script.** A generic-list page is a pure data container, edited entirely through Page Properties
  and the Create Page wizard; there is nothing meaningful to preview, so no render script is provided at all.
- **The `allowedPaths` root.** Upstream defaulted to `/etc/acs-commons/lists`; this module defaults to
  `/content/generic-lists(/.*)?` instead, since `/etc` is deprecated for custom content in AEM as a Cloud Service.
  Adjust `allowedPaths` in
  [`templates/generic-list/.content.xml`](ui.content/src/main/content/jcr_root/conf/acs-genericlists/settings/wcm/templates/generic-list/.content.xml)
  if you want a different root.

## Note on the template

Earlier versions of this module shipped a classic/static `cq:Template` under `/apps/acs-genericlists/templates`.
That's the same pattern upstream ACS Commons used, and it's precisely why upstream built its own
`/generic-lists.html` bulk-admin console - a static template doesn't show up in AEM as a Cloud Service's "Create
Page" wizard, which expects an **Editable Template** under `/conf`. This module now ships a proper Editable
Template (`ui.content`) instead: no responsive grid / container ceremony at all, since there's no content on the
page - `structure` and `initial` just point straight at the bare `genericlist` component, same as this project
would do for any pure config/data page.
