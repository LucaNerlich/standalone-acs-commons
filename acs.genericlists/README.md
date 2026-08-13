# acs.genericlists

In-house replacement for ACS AEM Commons' Generic Lists feature. A Generic List is primarily a standalone,
headless AEM resource containing an ordered collection of key/value pairs. The included page, editable template,
and component UI are optional authoring conveniences.

## Architecture

- `acs-genericlists/components/key-value-list` is the canonical Generic List resource type. Code adapts this
  resource directly to `GenericList`; it does not require a `cq:Page`.
- `acs-genericlists/components/page` is a hidden proxy of `core/wcm/components/page/v3/page`. For convenience,
  `Page#adaptTo(GenericList.class)` delegates to `jcr:content/root/keyValueList`.
- `acs-genericlists/components/key-value-list` owns the composite multifield and Coral UI 3 table preview.
- `/conf/acs-genericlists/settings/wcm/templates/generic-list` is an optional editable template restricted to
  `/content/generic-lists(/.*)?`.
- The in-house pre-1.1.1 resource type remains readable. Original
  `acs-commons/components/utilities/genericlist` resource types are intentionally not supported.

## Headless storage and Java API

The canonical resource shape is:

```text
/content/config/countries
    sling:resourceType = "acs-genericlists/components/key-value-list"
    items/item0
        jcr:title = "Germany"
        value     = "de"
        translations/item0
            locale = "de-CH"
            title  = "Deutschland (CH)"
```

Adapt the canonical resource directly:

```java
Resource listResource = resourceResolver.getResource("/content/config/countries");
GenericList list = listResource.adaptTo(GenericList.class);

for (GenericList.Item item : list.getItems()) {
    // item.getTitle() is the authored key; item.getValue() is its value.
}

String key = list.lookupTitle("de"); // "Germany"
```

A page created from the supplied template remains a supported convenience:

```java
Page listPage = pageManager.getPage("/content/generic-lists/countries");
GenericList list = listPage.adaptTo(GenericList.class);
```

Localized titles resolve in this order: exact locale, language-only locale, then the default `jcr:title`.
Existing dotted properties such as `jcr:title.de_ch` remain readable; the component dialog authors scalable
`translations/<row>/{locale,title}` entries.

## Granite UI datasource

The datasource accepts either a standalone Generic List resource or a convenience page:

```xml
<country
        jcr:primaryType="nt:unstructured"
        sling:resourceType="granite/ui/components/coral/foundation/form/select"
        fieldLabel="Country"
        name="./country">
    <datasource
            jcr:primaryType="nt:unstructured"
            sling:resourceType="acs-genericlists/components/utilities/genericlist/datasource"
            path="/content/config/countries"/>
</country>
```

Titles are localized using the request locale.

## JSON contracts

### `list.json`

A list page or standalone list resource exposes the ordered ACS-compatible array contract:

```http
GET /content/config/countries.list.json
```

```json
[
  { "value": "de", "text": "Germany" }
]
```

### `/mnt/acs-commons/lists`

The compatibility resource provider exposes JSON below `/mnt/acs-commons/lists`. By default it maps relative
paths to `/etc/acs-commons/lists`; configure **ACS Generic Lists - JSON Resource Provider** to use another
headless root such as `/content/config`.

```http
GET /mnt/acs-commons/lists/countries.json
```

```json
{
  "options": [
    { "text": "Germany", "title": "Germany", "value": "de" }
  ]
}
```

The provider uses the caller's resource resolver and therefore preserves repository permissions.

## Building and deploying

From this module:

```bash
mvn clean verify
mvn clean install -PautoInstallBundle
mvn clean install -PautoInstallPackage
```

The default target is `localhost:4502`; override the Maven deployment properties for other environments.

## Deliberately not ported

- The ACS Commons MCP-generated Classic UI dialog and `/generic-lists.html` bulk admin console. A static Coral 3
  component dialog supplies the fixed schema without the MCP form-generation dependency.
- Original ACS Commons resource types. Migrate content to
  `acs-genericlists/components/key-value-list`; the historic `/mnt/acs-commons/lists` URL is retained only as an
  HTTP/resource-provider compatibility contract.
