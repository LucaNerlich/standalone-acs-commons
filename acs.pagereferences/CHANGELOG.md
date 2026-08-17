# Changelog
All notable changes to this module are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.0] - 2026-08-17
### Added
- Initial standalone release: `PagesReferenceProvider`, a slim replacement for ACS Commons' Pages Reference
  Provider, built directly on the native AEM `ReferenceProvider` API. Modernized to R7 OSGi DS annotations
  (`org.osgi.service.component.annotations` + `org.osgi.service.metatype.annotations`), same behavior and
  `configurationPolicy = REQUIRE` semantics as upstream.
