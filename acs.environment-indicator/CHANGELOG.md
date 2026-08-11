# Changelog

All notable changes to this module are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

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
