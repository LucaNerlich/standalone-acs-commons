# Changelog

All notable changes to this module are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.1] - 2026-08-11

### Fixed

- `hide` on the included snippet's own root node (as opposed to one of its descendants) was silently ignored -
  `include.jsp` never checked the wrapped root's own `hide` before including it, only `getChild()`/`listChildren()`
  checked it for descendants. `include.jsp` now also skips the whole include when the root itself resolves to
  `hide="true"`.

## [1.0.0] - 2026-08-11

### Added

- Initial standalone release: `ParameterizedResourceWrapper` / `Placeholder` (`${{key:default}}` substitution),
  plus three opt-in capabilities on top of the base ACS AEM Commons "Parameterized Include for Dialog Widgets"
  contract:
  - namespace cascading (`namespace` attribute, `ParameterizedResourceWrapper.cascadeNamespace`)
  - conditional visibility (`hide` property)
  - typed placeholder casting (`${{(Boolean|Long|Double)key:default}}`)
- Granite UI component (`ui.apps`/`ui.apps.structure`) registering `acs-include/granite/ui/components/include`.
