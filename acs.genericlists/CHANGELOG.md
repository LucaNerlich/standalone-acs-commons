# Changelog

All notable changes to this module are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.1.1] - 2026-08-13

### Changed

- Split the former all-in-one `genericlist` page component into a clean
  `acs-genericlists/components/page` proxy of Core Page v3 and a standalone
  `acs-genericlists/components/key-value-list` content component.
- Rebuilt the editable template and its template type with a Core Container root and an editable Key/Value List
  component in the structure/initial merge. Authors now edit the ordered pairs through the component dialog.
- Added a Coral UI 3 table preview of the authored pairs and registered the list resource as a Sling Model.
- Retained `Page#adaptTo(GenericList.class)` support for pages created with version 1.1.0.

### Fixed

- The table preview used bare `<coral-table>`/`<coral-table-row>` custom-element tags, which never got upgraded
  by Coral's JS on render and were left as unstyled unknown elements (default `display: inline`), collapsing the
  whole table onto one line. Switched to the `is="coral-table"` attribute-upgrade pattern on native
  `<table>`/`<tr>`/`<td>` elements instead - the same convention this project's own `pim-import.html` and
  `localuserreport.html` already use - so the markup renders correctly as a table even before any JS upgrade
  happens.

## [1.1.0] - 2026-08-12

### Changed

- **Breaking (for existing content):** replaced the static `/apps/acs-genericlists/templates/genericlist`
  `cq:Template` with a proper Editable Template at
  `/conf/acs-genericlists/settings/wcm/templates/generic-list` (new `ui.content` module), so list pages can be
  created through the standard Sites console "Create Page" wizard instead of only via CRXDE/repoinit. Pages
  authored against the old static template keep working (same resource type), but their `cq:template` property is
  now stale and should be updated to the new path.
- The `genericlist` page component now extends `core/wcm/components/page/v3/page` (`sling:resourceSuperType`),
  purely so its dialog inherits the standard "Basic" tab and so the Create Page wizard's properties step renders at
  all - without it, that step is empty and "Create" stays permanently disabled. `sling:hideChildren` on the
  dialog's tabs then suppresses everything else that inheritance brings along (Images, Advanced, Cloud Services,
  Personalization, Permissions, PWA, Blueprint, Live Copy, Preview URL), none of which applies to a pure data/config
  page. Our own "List Items" tab is marked `cq:showOnCreate="{Boolean}false"`, so it's hidden during the wizard
  entirely (a composite multifield's "Add" button doesn't produce usable Title/Value inputs there - AEM's wizard
  extracts and renders `cq:showOnCreate` fields in isolation from the rest of the dialog, and that isolated
  rendering doesn't reliably produce the template markup a composite multifield needs; see the README) and shows
  only in Page Properties, right after creation. The page itself still renders nothing - editing stays entirely in
  Page Properties, same as before.
- Removed the page's own Title/Description dialog fields - redundant now that the inherited "Basic" tab provides
  them. The dialog is a single "List Items" tab, wrapped in the standard `cq-dialog-content-page` /
  `cq-siteadmin-admin-properties-tabs` structure (matching this project's own `keyvalue-config-page`) so it's laid
  out at the same width as the rest of Page Properties.
- Fixed `GenericListImpl` to read rows from `list/item` (falling back to `list`'s own direct children for content
  authored without going through a dialog, e.g. via repoinit) - Granite's composite multifield nests rows one level
  deeper than a naive reading of the dialog's `name` attribute would suggest; see the README's "Storage detail"
  note.

## [1.0.1] - 2026-08-12

### Changed

- Replaced the `datasource` JSP with `GenericListDataSourceServlet`, a Java servlet registered against the same
  `genericlist/datasource` resource type with identical behavior - avoids JSP scripting, consistent with how the
  rest of this module is implemented.

## [1.0.0] - 2026-08-11

### Added

- Initial standalone release: `GenericList` / `GenericListImpl` / `GenericListAdapterFactory`, a slim replacement
  for ACS Commons' Generic Lists feature, adapting a `Page` to an ordered list of title/value pairs.
- Granite UI touch-UI dialog (`ui.apps`/`ui.apps.structure`) authoring the list via a composite multifield, a
  `genericlist` page template, and a `datasource` JSP for feeding dialog field options from a list page - all
  without a dependency on ACS Commons' MCP form-generation framework (see the module README's "Not ported"
  section).
