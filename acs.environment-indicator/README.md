# acs.environment-indicator

In-house replacement for ACS AEM Commons' "Show Author Environment Indicator" feature. Renders a small, colored
badge in the AEM author admin UI (Sites, Assets, CRXDE, the page editor chrome, ...) so authors can tell
environments apart (e.g. `DEV` vs `STAGE` vs `PROD`) at a glance, regardless of whether Adobe's Unified Shell is
in front of AEM or not.

## How it works

Adobe's Unified Shell already
renders its own generic environment badge (`span.colorBadge` inside `#env-labels`) next to the AEM logo. This
module's clientlib (`js/env-indicator.js`) looks up that badge in `window.parent.document` and overwrites its text
with a friendly label derived from the current author hostname.

When the Unified Shell isn't present - a local AEM author, or an AEM as a Cloud Service author reached directly at
its Cloud Manager URL, bypassing `experience.adobe.com` - there's no such badge to reuse. The script then renders
its **own** badge instead, inserted right after the classic shell's home anchor label
(`<coral-shell-homeanchor-label>Adobe Experience Manager</coral-shell-homeanchor-label>`.

No HTL/component wiring is needed to load the clientlib: it's registered under the standard Granite admin shell
categories (`granite.ui.foundation`, `granite.ui.foundation.admin`, `granite.ui.coral.foundation`,
`granite.ui.coral.foundation.addon.coral2`), which AEM already includes on every admin console page (Sites, Assets,
CRXDE Lite, `start.html`, the page editor chrome, ...). Deploying the package is the only setup step.

## Modules

- `ui.apps` / `ui.apps.structure` - the clientlib itself, at
  `/apps/acs-environment-indicator/clientlibs/clientlib-env-indicator`.

## Building & deploying

This module builds standalone against any AEM as a Cloud Service instance:

```bash
mvn clean install                                   # build all modules
mvn clean install -PautoInstallPackage              # build + deploy the ui.apps/ui.apps.structure content packages
```

Override `aem.host`/`aem.port` (and `vault.user`/`vault.password` if not `admin`/`admin`) via `-D` if your instance
differs from the defaults.

## Customizing the labels

Edit `DOMAIN_LABELS` and `KNOWN_LABELS` at the top of
[`env-indicator.js`](ui.apps/src/main/content/jcr_root/apps/acs-environment-indicator/clientlibs/clientlib-env-indicator/js/env-indicator.js):

```js
const DOMAIN_LABELS = {
    'author-p12345-e123456.adobeaemcloud.com': 'DEV',
    'author-p12345-e123457.adobeaemcloud.com': 'STAGE',
    'author-p12345-e123458.adobeaemcloud.com': 'PROD',
    'localhost': 'LOCAL',
};

const KNOWN_LABELS = ['DEV', 'STAGE', 'PROD', 'LOCAL'];
```

The hostnames are each environment's "Author" URL, found under Cloud Manager > Environments. Any hostname not in
the map renders as `Unknown` (gray). `localhost` is included by default so the fallback badge is visible on a
local author instance too.

The badge's colors live in
[`env-indicator.css`](ui.apps/src/main/content/jcr_root/apps/acs-environment-indicator/clientlibs/clientlib-env-indicator/css/env-indicator.css)
as `.acs-env-indicator-badge--<label>` modifier classes (lowercased label, e.g. `--dev`, `--stage`, `--prod`,
`--local`), with `--unknown` as the fallback for any label not in `KNOWN_LABELS`. If you add a label to
`DOMAIN_LABELS`/`KNOWN_LABELS`, add a matching color class to the CSS as well.
