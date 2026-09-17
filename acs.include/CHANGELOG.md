# Changelog

All notable changes to this module are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.1.0] - 2026-09-17

### Added

- `ParameterizedResourceWrapper.cascadeParameters()` - a nested `acs-include` with no `parameters` child of its own
  now automatically inherits whatever an ancestor `acs-include` already set, mirroring the existing `cascadeNamespace`
  behavior for the `namespace` attribute. A `parameters` child is only needed to add a new key or override an ambient
  one for that subtree, not to relay a value back under the same name. `include.jsp` updated to build its own
  parameters map and cascade it against the ambient one before wrapping.
- `ParameterizedResourceWrapper.wrapParameters(Resource, Map<String, String>, String)` - wraps directly from an
  already-built parameters map (e.g. the result of `cascadeParameters`), without round-tripping through a
  `parameters` child `Resource`. Named distinctly from `wrap()` rather than overloading it, since a `null` second
  argument would otherwise be ambiguous between the `Resource` and `Map` overloads.
- `ParameterizedResourceWrapper.toParameterMap()` made public, so callers building a cascaded map (like
  `include.jsp`) can compute their own parameters before merging.

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
