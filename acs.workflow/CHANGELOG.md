# Changelog

All notable changes to this module are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.0] - 2026-08-11

### Added

- Initial standalone release: `WorkflowPackageManager` / `WorkflowPackageManagerImpl`, a slim replacement for
  ACS Commons' Workflow Package Manager, built directly on native AEM Workflow collection APIs
  (`ResourceCollectionUtil`/`ResourceCollectionManager`). Needs no service user, unlike upstream, since the
  bucket-path lookup uses the caller's own `ResourceResolver` instead of an activation-time service resolver.
