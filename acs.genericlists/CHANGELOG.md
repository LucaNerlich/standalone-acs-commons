# Changelog

All notable changes to this module are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

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
