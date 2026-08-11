# acs.include

In-house replacement for ACS AEM Commons' "Parameterized Include for Dialog Widgets"
(`acs-commons/granite/ui/components/include`). Lets a Granite UI dialog `path`-include a reusable field snippet and
substitute `${{key:default}}` placeholders in that snippet's properties via a `parameters`
child node - the same authoring contract ACS Commons offered, plus three opt-in capabilities described below.

## Modules

- `core` - `com.adobe.acs.include.ParameterizedResourceWrapper`: wraps a resource (recursively, for its whole subtree)
  so String properties matching `${{key:default}}` are substituted from a flat
  `Map<String, String>` built from the include's `parameters` child resource.
- `ui.apps` / `ui.apps.structure` - the Granite UI component itself: `include.jsp` at
  `/apps/acs-include/granite/ui/components/include`, registered under the new resource type
  `acs-include/granite/ui/components/include`.

## Building & deploying

This module builds standalone against any AEM as a Cloud Service instance:

```bash
mvn clean install                                   # build all modules
mvn clean install -PautoInstallBundle               # build + deploy the core bundle (default: localhost:4502)
mvn clean install -PautoInstallPackage              # build + deploy the ui.apps/ui.apps.structure content packages
```

Override `aem.host`/`aem.port` (and `vault.user`/`vault.password` if not `admin`/`admin`) via `-D` if your instance
differs from the defaults.

## Authoring contract

```xml
<caption
        jcr:primaryType="nt:unstructured"
        sling:resourceType="acs-include/granite/ui/components/include"
        path="myapp/widgets/textarea/textarea">
    <parameters
            jcr:primaryType="nt:unstructured"
            fieldLabel="Caption"
            propertyName="./caption"/>
</caption>
```

`path` is resolved via `resourceResolver.getResource(path)`, so it's a relative, search-path-style reference (`/apps`
then `/libs`) - no leading slash, matching how it was always authored. The included snippet's own properties use
`${{key:default}}`:

```xml
<textarea
        jcr:primaryType="nt:unstructured"
        sling:resourceType="granite/ui/components/coral/foundation/form/textarea"
        fieldLabel="${{fieldLabel:Text}}"
        name="${{propertyName:./text}}"/>
```

If `parameters` supplies a key, its value is used; otherwise the literal text after the `:` is used (or an empty string
if there's no `:` at all).

## Opt-in feature: namespace cascading

**What it's for:** including the *same* snippet more than once in one dialog without the substituted `name` (and
similar) properties colliding.

```xml
<block1
        jcr:primaryType="nt:unstructured"
        sling:resourceType="acs-include/granite/ui/components/include"
        namespace="block1"
        path="myapp/widgets/textwithlink/textWithLink"/>
<block2
        jcr:primaryType="nt:unstructured"
        sling:resourceType="acs-include/granite/ui/components/include"
        namespace="block2"
        path="myapp/widgets/textwithlink/textWithLink"/>
```

If `textWithLink` sets `name="./text"` internally, `block1`'s field is stored as `./block1/text` and `block2`'s as
`./block2/text` instead of both writing to `./text`. Namespacing only rewrites a fixed set of property names -
`name`, `fileNameParameter`, `fileReferenceParameter` - everything else (labels, descriptions, ...) is untouched. It
cascades into nested includes: an `acs-include` node found inside an already-namespaced snippet inherits the ambient
namespace, combined with its own `namespace` attribute (if any) as `ambient/own`. It does **not** cascade into a
`multifield`'s fields - a multifield's own per-row indexing already guarantees uniqueness, so namespacing its fields
would only break that.

## Opt-in feature: conditional visibility (`hide`)

**What it's for:** excluding a (potentially nested) child of an included snippet from rendering, based on a
placeholder-resolved condition.

```xml
<!-- inside the included snippet -->
<advancedOptions
        jcr:primaryType="nt:unstructured"
        sling:resourceType="granite/ui/components/coral/foundation/container"
        hide="${{hideAdvanced:true}}">
    <items jcr:primaryType="nt:unstructured">
        <!-- ... -->
    </items>
</advancedOptions>
```

```xml
<!-- at the include site -->
<caption
        jcr:primaryType="nt:unstructured"
        sling:resourceType="acs-include/granite/ui/components/include"
        path="myapp/widgets/something/something">
    <parameters
            jcr:primaryType="nt:unstructured"
            hideAdvanced="false"/>
</caption>
```

`hide` is resolved like any other property (so it can itself be a `${{key:default}}` placeholder), then parsed as a
boolean; a resource with `hide="true"` (after substitution) is skipped - it and its whole subtree don't render. A
literal, non-placeholder `hide="true"`/`hide="false"` works too. No `hide` property (the default for every current
snippet) means visible, as before.

This applies both to a descendant of the included snippet (filtered out via `getChild()`/`listChildren()` by its
parent, as in the example above) **and** to the snippet's own root node - i.e. `hide` on the very resource `path`
points to skips the whole include, not just its children. `include.jsp` checks the wrapped root's own `hide` before
calling `cmp.include(...)`, since there's no parent wrapper around the top-level include to have filtered it out.

## Opt-in feature: typed casting

**What it's for:** normalizing a substituted (or default) value to a canonical `Boolean`/`Long`/`Double` string
representation before it lands in the property, instead of passing through whatever string happened to be supplied.

```xml
<properties
        jcr:primaryType="nt:unstructured"
        sling:resourceType="granite/ui/components/coral/foundation/tabs"
        maximized="${{(Boolean)maximized:false}}"/>
```

Given a `parameters` node with `maximized="TRUE"`, the plain `${{maximized:false}}` form would leave the property as
the literal string `"TRUE"`. With the `(Boolean)` prefix it's canonicalized to `"true"`. Supported type hints:
`Boolean` (`Boolean.parseBoolean`), `Long`, `Double` - case-insensitive. If the chosen value can't be parsed as the
requested numeric type, the original value is kept as-is rather than substituting garbage or failing the include.
The property is still a String afterwards (Sling's `ValueMap.get(key, Boolean.class)` already coerces
canonical `"true"`/`"false"` strings on read, same as it does for any other boolean dialog property) - only the
*content* of the string is normalized, not its Java type.

Omit the `(Type)` prefix (as every current placeholder does) and substitution behaves exactly as before.
