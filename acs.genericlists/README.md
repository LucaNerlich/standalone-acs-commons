# ACS Generic Lists

A modern, headless-first replacement for the ACS AEM Commons Generic Lists feature. Version **1.3.0** keeps the
useful ACS contracts while adding a guarded HTL model, deterministic validation/localization, migration tooling,
a standalone author console, and a single installable package.

## What is canonical

A Generic List is a resource of type `acs-genericlists/components/key-value-list`:

```text
/content/config/countries
    sling:resourceType = "acs-genericlists/components/key-value-list"
    jcr:title          = "Countries"                     (optional metadata)
    jcr:description    = "Profile country selector"      (optional metadata)
    defaultLocale      = "en"                            (optional BCP-47 metadata)
    supportedLocales   = ["en", "de-CH"]                (optional BCP-47 metadata)
    items/item0
        jcr:title = "Germany"
        value     = "de"
        translations/item0
            locale = "de-CH"
            title  = "Deutschland (CH)"
```

`items` keeps authored order. A supplied editable template/page is an **optional authoring wrapper**, not a
requirement for Java, Granite datasource, JSON, migration, or console use.

## Supported Java APIs

New integrations should use the stable exported API package:

```java
import com.adobe.acs.genericlists.api.GenericList;

Resource resource = resourceResolver.getResource("/content/config/countries");
GenericList list = resource.adaptTo(GenericList.class);

for (GenericList.Item item : list.getItems()) {
    // values are ordered, unique, and nonblank
}
String title = list.lookupTitle("de", Locale.forLanguageTag("de-CH"));
```

The former standalone API, `com.adobe.acs.genericlists.GenericList`, remains exported as a deprecated compatibility
facade in 1.3.0. New implementation classes live in a private package; deprecated 1.x class facades remain only for
binary compatibility, so new code should consume the API interfaces only.

### Optional ACS Commons Java bridge

`acsgenericlists.compat` is an **optional** bundle that exports
`com.adobe.acs.commons.genericlists.GenericList` and adapts existing `Resource`/`Page` consumers to the modern
implementation. It is useful during a controlled source/binary migration, but **must not be installed alongside an
ACS Commons bundle that exports the same package**. The aggregate `all` package deliberately does not embed it.

## Schema, validation, and localization

The reusable `GenericListSchema` validates content independently of the dialog. This means imports, packages,
CRXDE changes, custom workflows, and migration tooling all share the same rules:

- At most **500** rows per list.
- Each title and value is nonblank and at most **255** characters.
- Values are unique. The first valid value wins; later duplicates are excluded from iteration, lookup, datasource,
  and JSON output so all delivery paths agree.
- At most **50** localized titles per item.
- Locales are normalized BCP-47 tags and are unique per item.
- Invalid rows are skipped safely and exposed through `GenericList#getValidationIssues()` rather than producing
  ambiguous output.

Localized lookup accepts legacy dotted properties (`jcr:title.de_ch`) as well as `translations` rows. Its fallback
chain is deterministic. For example:

```text
zh-Hant-TW -> zh-Hant -> zh-TW -> zh -> default jcr:title
```

The author dialog provides immediate client-side whitespace, duplicate-value, duplicate-locale, length, and BCP-47
feedback. The server-side schema remains authoritative.

## Authoring

### Modern author console for standalone lists

On Author, open:

```text
/bin/acs-genericlists/console
```

The console uses the current authenticated user and the management API below; it is intentionally permission-aware
rather than using a broad service user. It supports:

- browse/search under configured roots;
- create, copy, move, and delete;
- JSON and CSV export/import with validation before replacement;
- duplicate/error visibility;
- configurable metadata;
- where-used scanning below configured search roots;
- publish/unpublish and status when AEM replication is available.

The default writable roots are `/content/generic-lists` and `/content/config`; configure **ACS Generic Lists -
Management API** for a different governance model. Normal AEM CSRF protection applies to POST operations.

### Page-based authoring

The supplied template at
`/conf/acs-genericlists/settings/wcm/templates/generic-list` creates a page under `/content/generic-lists` with a
Key/Value List component. It remains suitable for author teams that prefer the standard Sites console and Page
Editor. The component preview is **author-only**; publish rendering is empty and does not load `coralui3`.

For customized page templates, set `genericListPath` on the page content to an absolute or relative canonical list
path. Without it, the adapter supports the historic `root/keyValueList` location and, safely, a template containing
exactly one canonical list component.

### Dialog-scale operations

The nested composite multifield remains ideal for small-to-medium lists. For larger lists, use the console’s CSV or
JSON import/export instead of manually editing hundreds of rows.

## Granite UI datasource

Use either canonical resources or supported list pages:

```xml
<country
        jcr:primaryType="nt:unstructured"
        sling:resourceType="granite/ui/components/coral/foundation/form/select"
        fieldLabel="Country"
        name="./country">
    <datasource
            jcr:primaryType="nt:unstructured"
            sling:resourceType="acs-genericlists/components/utilities/genericlist/datasource"
            path="/content/config/countries"
            locale="de-CH"
            sortBy="authored"
            includeEmptyOption="{Boolean}true"
            emptyText="Choose a country"
            disabledValues="[deprecated]"/>
</country>
```

`acs-commons/components/utilities/genericlist/datasource` remains accepted during migration. Optional datasource
properties:

| Property | Values | Default |
| --- | --- | --- |
| `path` | canonical list resource or list page | required |
| `locale` | BCP-47 locale | request locale |
| `sortBy` | `authored`, `title`, `value` | `authored` |
| `includeEmptyOption` | boolean | `false` |
| `emptyText` | string | empty string |
| `disabledValues` | string or string array | none |

## JSON delivery

### `list.json`

A canonical list, modern page, in-house legacy list, or original ACS Commons list exposes:

```http
GET /content/config/countries.list.json?locale=de-CH
```

```json
[
  { "value": "de", "text": "Deutschland (CH)" }
]
```

When `locale` is absent, the request locale/`Accept-Language` is used. Responses set `Vary: Accept-Language` and
`Content-Language`.

### Historic `/mnt/acs-commons/lists`

The compatibility provider retains the ACS options shape:

```http
GET /mnt/acs-commons/lists/countries.json?locale=de-CH
```

```json
{
  "options": [
    { "text": "Deutschland (CH)", "title": "Deutschland (CH)", "value": "de" }
  ]
}
```

Its default ordered roots are:

1. `/content/generic-lists` (modern page-backed content)
2. `/etc/acs-commons/lists` (native ACS legacy content)

Configure **ACS Generic Lists - JSON Resource Provider** to add or replace roots. The provider and JSON servlets use
the caller’s resolver, preserving repository permissions.

## ACS migration

Runtime compatibility reads the original resource type
`acs-commons/components/utilities/genericlist`, legacy in-house content, and the canonical type. Use the explicit,
idempotent migration service to write modern content; no content is mutated automatically at activation or package
install.

The Author migration endpoint is:

```text
POST /bin/acs-genericlists/migrate
```

Parameters:

| Parameter | Meaning |
| --- | --- |
| `path` | source Generic List resource or page |
| `targetPath` | canonical destination; use the source page path for an in-place page upgrade |
| `dryRun` | defaults to `true`; inspect before mutation |
| `overwrite` | permits replacing an existing standalone target or component |

An in-place legacy page migration updates the page resource type/template, creates `root/keyValueList`, and preserves
legacy source rows for verification. A standalone migration creates a canonical resource. Reports include migrated,
skipped, and validation diagnostics in JSON.

## Security, publish behavior, and caching

Generic List values often contain internal identifiers. This module deliberately does **not** render the component
preview on publish. JSON is an explicit delivery surface and must be governed by your site’s ACLs and dispatcher/CDN
rules:

1. Deny anonymous read access to `/content/generic-lists` and headless roots unless a list is explicitly intended for
   public JSON use.
2. Allow only the needed `.list.json` and `/mnt/acs-commons/lists/*.json` paths at the dispatcher.
3. Respect `Vary: Accept-Language` for localized JSON caches; do not cache a localized response under a language-
   neutral key.
4. Restrict `/bin/acs-genericlists/console`, `/bin/acs-genericlists/lists`, and
   `/bin/acs-genericlists/migrate` to trusted Author users. Keep CSRF protection enabled.
5. Review values and metadata before activating a list. The console uses normal AEM replication permissions.

## Building and deploying

Requirements:

- Java **21**;
- the pinned AEM SDK API (`2025.8.21994.20250818T175115Z-250800`);
- Core Components Page v3 and Container v1 for the optional page/template experience. AEM as a Cloud Service normally
  provides these; projects deploying only the headless API do not need the page/template modules.

Build all Generic Lists artifacts:

```bash
mvn -f acs.genericlists/pom.xml clean verify
```

Install the **single aggregate package**:

```text
acs.genericlists/all/target/acsgenericlists.all-1.3.0.zip
```

It embeds the core bundle, repository structure, UI application, and template content. The optional ACS Java bridge
is built separately as:

```text
acs.genericlists/compat/target/acsgenericlists.compat-1.3.0.jar
```

For local development:

```bash
mvn -f acs.genericlists/pom.xml clean install -PautoInstallBundle
mvn -f acs.genericlists/pom.xml clean install -PautoInstallPackage
```

### AEM SDK smoke test checklist

After package installation on an Author and Publish SDK, verify:

1. Create a page with the supplied template and edit/add/remove/reorder nested localized rows.
2. Confirm the author preview works and Publish renders no preview table or Coral client library.
3. Create a standalone list through `/bin/acs-genericlists/console`; import/export CSV and JSON.
4. Verify datasource sorting/localization, `.list.json`, and `/mnt/acs-commons/lists` with a non-default locale.
5. Run migration with `dryRun=true`, inspect the report, then migrate a copy of legacy content.
6. Verify ACLs, dispatcher rules, and anonymous JSON behavior deliberately rather than by accident.
