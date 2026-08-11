# Changelog

All notable changes to this module are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.1] - 2026-08-11

### Fixed

- Badge styling moved out of inline JS styles into `env-indicator.css`, nested under `.acs-env-indicator-badge`
  using native CSS nesting. Color/fixed-position variants are applied via a second `.is-<label>`/`.is-fixed` class
  rather than a `--<label>` suffix.
- Simplified the badge's `border-radius` to a static `0.5rem`. It previously replicated the Unified Shell's own
  `round(calc(var(--radius) * var(--size)), 1px)` formula, but `--radius`/`--size` were always constants here, so
  the whole `calc()`/`round()` indirection reduced to a no-op around a fixed value.

## [1.0.0] - 2026-08-11

### Added

- Initial standalone release: `env-indicator.js` clientlib rendering a colored AEM author environment badge,
  reusing the Unified Shell's own badge (`span.colorBadge`) when present, and rendering a self-contained fallback
  badge next to the classic shell's home anchor label when it isn't (e.g. local author instances).
- Granite UI admin clientlib (`ui.apps`/`ui.apps.structure`) at
  `/apps/acs-environment-indicator/clientlibs/clientlib-env-indicator`, registered under the standard
  `granite.ui.foundation`/`granite.ui.foundation.admin`/`granite.ui.coral.foundation`/
  `granite.ui.coral.foundation.addon.coral2` categories so it loads on every admin console page without further
  wiring.
