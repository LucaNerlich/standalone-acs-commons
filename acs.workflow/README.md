# acs.workflow

In-house replacement for ACS AEM Commons' `WorkflowPackageManager`. Creates and reads AEM Workflow "packages" -
`cq:Page`s under `/var/workflow/packages` that hold a multi-path payload (e.g. the set of pages a bulk
publish/lock/delete workflow acts on) - built directly on native AEM Workflow collection APIs.

## How it works

A "workflow package" is just a `cq:Page` (template `/libs/cq/workflow/templates/collectionpage`) with a
`vlt:definition` node underneath describing which paths it contains, using the exact same structure the AEM
Workflow console itself creates when you select multiple items and start a workflow.

Calling `create(resourceResolver, name, paths...)`:

1. Resolves the bucket root from the *caller's own* `ResourceResolver` - `/var/workflow/packages` if it exists,
   falling back to the legacy `/etc/workflow/packages` otherwise. No service user or activation-time lookup is
   needed for this, unlike upstream.
2. Creates the page and a `vlt:definition/filter` node listing one `resource_N` child per given path, each with
   `root` (the path) and `rules` (glob include rules matching that path and its `jcr:content` subtree).
3. Sets the page's own `sling:resourceType` to `cq/workflow/components/collection/page` explicitly, so
   `isWorkflowPackage()` is deterministic regardless of what the `collectionpage` template itself defines.

Calling `getPaths(resourceResolver, path)`:

1. If `path` doesn't resolve to a resource: returns an empty list.
2. If `path` doesn't resolve to a workflow package (i.e. wasn't created by `create()`): returns a single-item
   list containing `path` itself, unchanged.
3. Otherwise: delegates to AEM's own `com.day.cq.workflow.collection.ResourceCollectionUtil`/`ResourceCollection`
   to interpret the `vlt:definition` filter rules and enumerate the actual matching paths.

## Modules

- `core` - `com.adobe.acs.workflow.WorkflowPackageManager` / `WorkflowPackageManagerImpl`: `create`, `getPaths`,
  `delete`, `isWorkflowPackage`.

## Building & deploying

```bash
mvn clean install                       # build
mvn clean install -PautoInstallBundle   # build + deploy the bundle (default: localhost:4502)
```

Override `aem.host`/`aem.port` via `-D` if your instance differs from the defaults.

## Usage

```java
@Reference
private WorkflowPackageManager workflowPackageManager;

Page pkg = workflowPackageManager.create(resourceResolver, "bulk-publish", "my-package",
        "/content/site/page-a", "/content/site/page-b");

// ... start a workflow with pkg.getPath() as the payload ...

List<String> paths = workflowPackageManager.getPaths(resourceResolver, pkg.getPath());
workflowPackageManager.delete(resourceResolver, pkg.getPath());
```

No service user mapping is required - unlike upstream (and unlike this repo's `acs.email` module), every method
takes the caller's own `ResourceResolver`, so this module needs no dedicated service user or ACL setup at all.

## Not ported: configurable `wf-package.types`

Upstream exposes an OSGi `@Property` (`wf-package.types`, default `cq:Page,cq:PageContent,dam:Asset`) to change
the *default* node types `getPaths(resourceResolver, path)` (the 2-arg overload) filters by. This module hardcodes
that same default instead of making it OSGi-configurable, since the interface already exposes an explicit
3-arg `getPaths(resourceResolver, path, nodeTypes)` overload for callers who need different types - adding a
second, OSGi-config-driven way to change the same thing would just be two ways to do one thing. If you need a
different systemwide default, change `DEFAULT_WORKFLOW_PACKAGE_TYPES` in
[`WorkflowPackageManagerImpl`](core/src/main/java/com/adobe/acs/workflow/WorkflowPackageManagerImpl.java) directly.
