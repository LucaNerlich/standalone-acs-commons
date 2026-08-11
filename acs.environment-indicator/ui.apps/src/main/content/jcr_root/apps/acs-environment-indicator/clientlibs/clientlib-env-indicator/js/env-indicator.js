(function () {
    // Map each AEM author hostname to a short, friendly label. Replace these examples with your own
    // Cloud Manager program/environment author hostnames (Cloud Manager > Environments > "Author" URL).
    // Also update the matching `--<label>` color modifier classes in env-indicator.css if you add labels.
    const DOMAIN_LABELS = {
        'author-p12345-e123456.adobeaemcloud.com': 'DEV',
        'author-p12345-e123457.adobeaemcloud.com': 'STAGE',
        'author-p12345-e123458.adobeaemcloud.com': 'PROD',
        'localhost': 'LOCAL',
    };

    const KNOWN_LABELS = ['DEV', 'STAGE', 'PROD', 'LOCAL'];

    const BADGE_ID = 'acs-env-indicator-badge';
    const BADGE_CLASS = 'acs-env-indicator-badge';
    const FIXED_MODIFIER_CLASS = `${BADGE_CLASS}--fixed`;

    function colorModifierClass(label) {
        return `${BADGE_CLASS}--${KNOWN_LABELS.includes(label) ? label.toLowerCase() : 'unknown'}`;
    }

    function setBadgeColor(badge, label) {
        Array.from(badge.classList)
            .filter((c) => c.startsWith(`${BADGE_CLASS}--`) && c !== FIXED_MODIFIER_CLASS)
            .forEach((c) => badge.classList.remove(c));
        badge.classList.add(colorModifierClass(label));
    }

    // The unified shell renders its own badge outside 'our' iFrame, in the parent document.
    // Same-origin access can still throw if the shell isn't actually present, hence the try/catch.
    function getUnifiedShellBadge() {
        try {
            if (window.parent && window.parent !== window) {
                return window.parent.document.querySelector('span.colorBadge');
            }
        } catch (e) {
            // parent not reachable (cross-origin or no unified shell) - fall back to our own badge
        }
        return null;
    }

    function createBadge() {
        const badge = document.createElement('span');
        badge.id = BADGE_ID;
        badge.className = BADGE_CLASS;
        return badge;
    }

    // Without the unified shell, the classic AEM shell home anchor ("Adobe Experience Manager")
    // is the closest local equivalent of the unified shell's env-labels slot, so the badge goes next to it.
    // Note: <coral-shell-homeanchor-label> is a custom element tag, not a class - use a tag selector.
    function showOwnBadge(label) {
        let badge = document.getElementById(BADGE_ID);
        if (!badge) {
            const homeAnchorLabel = document.querySelector('coral-shell-homeanchor-label');
            badge = createBadge();
            if (homeAnchorLabel) {
                homeAnchorLabel.insertAdjacentElement('afterend', badge);
            } else {
                badge.classList.add(FIXED_MODIFIER_CLASS);
                document.body.appendChild(badge);
            }
        }
        badge.innerText = label;
        setBadgeColor(badge, label);
    }

    function addEnvIndicator() {
        const currentHostname = window?.location?.hostname || '';
        const LABEL = DOMAIN_LABELS[currentHostname] || 'Unknown';

        const unifiedShellBadge = getUnifiedShellBadge();
        if (unifiedShellBadge) {
            unifiedShellBadge.innerText = LABEL;
        } else {
            showOwnBadge(LABEL);
        }
    }

    $(document).ready(window.setTimeout(addEnvIndicator, 500));
})();
