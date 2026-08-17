# acs.pagereferences

In-house replacement for ACS AEM Commons' `PagesReferenceProvider`. Contributes page-to-page link references (any
string property whose value contains a path under a configured page root) into AEM's native `ReferenceProvider`
mechanism - the same extension point that powers the Page Editor's "References" rail.

## How it works

`PagesReferenceProvider` implements `com.day.cq.wcm.api.reference.ReferenceProvider` directly - no ACS-specific
interface layer, since the native AEM API already defines the contract.

Calling `findReferences(resource)`:

1. Recursively walks `resource` and all its child resources.
2. For every string (or string array) property value that contains the configured page root path (default
   `/content/`), extracts every path-like substring in that value (single path, multiple quoted paths, or a
   comma/quote-separated list).
3. Resolves each extracted path to its containing page via `PageManager#getContainingPage`, and returns one
   `Reference` per distinct page found (excluding a page referencing itself via the same content resource that's
   being searched).

The component requires explicit OSGi configuration to activate (`configurationPolicy = REQUIRE`), matching upstream
ACS Commons behavior - an OSGi config node must exist for the PID (even with no properties set, to pick up the
default `/content/` root) before the service registers.

## Modules

- `core` - `com.adobe.acs.pagereferences.PagesReferenceProvider`: `findReferences`.

## Building & deploying

```bash
mvn clean install                       # build
mvn clean install -PautoInstallBundle   # build + deploy the bundle (default: localhost:4502)
```

Override `aem.host`/`aem.port` via `-D` if your instance differs from the defaults.

## Usage

Provide an OSGi configuration for PID `com.adobe.acs.pagereferences.PagesReferenceProvider` (even an empty one, to
pick up the `/content/` default):

```json
{}
```

or override the page root path:

```json
{
  "page.root.path": "/content/my-site/"
}
```

Once configured, the component registers itself as a `com.day.cq.wcm.api.reference.ReferenceProvider` OSGi service
like any other - consumers that inject `List<ReferenceProvider>` (e.g. to build a custom reference/dependency graph)
will pick it up automatically. If existing code filters registered `ReferenceProvider`s by fully-qualified class
name, update that filter from `com.adobe.acs.commons.wcm.impl.PagesReferenceProvider` to
`com.adobe.acs.pagereferences.PagesReferenceProvider`.
