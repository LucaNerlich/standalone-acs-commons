# standalone-acs-commons

A collection of standalone AEM as a Cloud Service modules, each an in-house replacement for one ACS AEM Commons
feature. Every module lives in its own top-level folder with its own `pom.xml` and has **no dependency on anything
else in this repo** - it's meant to be copied wholesale into a consumer project (or built here, standalone) without
dragging along the rest of this repo. The root `pom.xml` is a pure build-all convenience aggregator; it is
deliberately *not* the Maven `<parent>` of any module, so removing a module folder from the aggregator's `<modules>`
list (or just copying that folder elsewhere) doesn't break anything.

## Modules

- [`acs.include`](acs.include/README.md) - in-house replacement for ACS AEM Commons' "Parameterized Include for
  Dialog Widgets" (`acs-commons/granite/ui/components/include`).
- [`acs.email`](acs.email/README.md) - in-house replacement for ACS AEM Commons' `EmailService`.
- [`acs.environment-indicator`](acs.environment-indicator/README.md) - in-house replacement for ACS AEM Commons'
  "Show Author Environment Indicator", with a fallback badge for when the Unified Shell isn't present.
- [`acs.workflow`](acs.workflow/README.md) - in-house replacement for ACS AEM Commons' `WorkflowPackageManager`.
- [`acs.genericlists`](acs.genericlists/README.md) - in-house replacement for ACS AEM Commons' "Generic Lists"
  feature.

## Using a module in your own project

Copy the module's whole folder (e.g. `acs.include/`) into your repository and add it to your own root `pom.xml`'s
`<modules>` list (or build it independently, as its own reactor, exactly as it builds here). Nothing inside a module
folder references this repo, so no further changes are needed.

Alternatively, grab a pre-packaged `.zip` of a single module from the
[Releases page](https://github.com/LucaNerlich/standalone-acs-commons/releases) instead of copying from a checkout.

## Building everything here

```bash
mvn clean install                  # builds every module's core/ui.apps/ui.apps.structure
```

See each module's own README for its specific `-P` deployment profiles (e.g. `autoInstallBundle`).

---

> **Note:** Looking for a Vite-based frontend setup for AEM as a Cloud Service? Check out
> [aem-vite](https://github.com/LucaNerlich/aem-vite).
