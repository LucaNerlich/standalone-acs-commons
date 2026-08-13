# Changelog

All notable changes to this module are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.3.0] - 2026-08-13

### Added

- Added the exported `com.adobe.acs.genericlists.api` contract with immutable list snapshots, metadata,
  validation diagnostics, BCP-47 helpers, and an explicit migration service/report API. The former standalone API
  remains as a deprecated compatibility facade.
- Added deterministic server-side `GenericListSchema` validation: nonblank/length-limited rows, unique values,
  normalized unique locales, maximum list/translation sizes, and diagnostics for malformed repository data.
- Added full BCP-47 script/region fallback, including legacy dotted title-property compatibility.
- Added localized `list.json` and `/mnt/acs-commons/lists` delivery using `locale` or request language, with
  `Vary: Accept-Language` and `Content-Language` headers.
- Added runtime compatibility for original ACS Commons resource/data-source types and ordered `/mnt` roots that
  resolve modern `/content/generic-lists` content before legacy `/etc/acs-commons/lists` content.
- Added the optional `acsgenericlists.compat` bundle for controlled migration of Java consumers importing
  `com.adobe.acs.commons.genericlists.GenericList`.
- Added a dry-run-first migration service and authenticated JSON endpoint at `/bin/acs-genericlists/migrate`.
- Added a permission-aware author console at `/bin/acs-genericlists/console` plus management API for standalone
  list browse/search/create/copy/move/delete/import/export/where-used/publication operations.
- Added the `acsgenericlists.all` aggregate package that embeds the bundle, repository structure, UI application,
  and template content in one installable artifact.

### Changed

- Moved new implementation classes to a private `impl` package while retaining deprecated 1.x class facades for binary compatibility.
- Replaced HTL interface instantiation with a resource-type-scoped `KeyValueListModel`, restoring deterministic HTL
  rendering without making unrelated resources adaptable.
- The Key/Value List preview now renders only in Author mode and no longer ships Coral UI to Publish.
- Enhanced the author dialog with list metadata, BCP-47 guidance, client-side duplicate/length checks, and clearer
  scalable import/export guidance.
- Page adaptation now supports an explicit `genericListPath`, the historic component location, or exactly one
  canonical descendant, making customized templates safer.
- Enhanced Granite datasource options with configured locale, sorting, optional empty option/text, and disabled
  values while retaining authored order as the default.

### Fixed

- Fixed the 1.2.0 HTL regression caused by resolving the unregistered `GenericList` interface as a use object.
- Fixed divergent duplicate-value behavior: output and lookup now consistently retain the first valid occurrence
  and report later duplicates.
- Fixed the `/mnt/acs-commons/lists` default root so native ACS list content is actually readable during migration.

## [1.2.0] - 2026-08-13

### Added

- Added direct headless `Resource#adaptTo(GenericList.class)` support for the canonical
  `acs-genericlists/components/key-value-list` resource type.
- Added the ACS-compatible `.list.json` servlet and `/mnt/acs-commons/lists` JSON resource provider.
- Added authoring and lookup support for locale/title pairs below each row's `translations` child.

### Changed

- Generic List resources are now the primary API; pages and editable templates are optional authoring wrappers.
- The Granite datasource now accepts either a standalone list resource or a list page.
- Removed the unrestricted Sling Model adaptation that allowed unrelated resources to become empty lists.

### Fixed

- Fixed the in-house legacy adapter branch being shadowed by its resource supertype.
- Explicitly reject original `acs-commons/components/utilities/genericlist` resource types.

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
