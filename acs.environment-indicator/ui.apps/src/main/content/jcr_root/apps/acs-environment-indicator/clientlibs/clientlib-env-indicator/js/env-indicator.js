(function () {
    // Map each AEM author hostname to a short, friendly label. Replace these examples with your own
    // Cloud Manager program/environment author hostnames (Cloud Manager > Environments > "Author" URL).
    // Also update the matching `.is-<label>` color modifier classes in env-indicator.css if you add labels.
    const DOMAIN_LABELS = {
        'author-p12345-e123456.adobeaemcloud.com': 'DEV',
        'author-p12345-e123457.adobeaemcloud.com': 'STAGE',
        'author-p12345-e123458.adobeaemcloud.com': 'PROD',
        'localhost': 'LOCAL',
    };

    const KNOWN_LABELS = ['DEV', 'STAGE', 'PROD', 'LOCAL'];

    const BADGE_ID = 'acs-env-indicator-badge';
    const BADGE_CLASS = 'acs-env-indicator-badge';
    const FIXED_MODIFIER_CLASS = 'is-fixed';

    /**
     * Generates a CSS class string based on the provided label.
     * If the label exists in the predefined list of known labels, it returns the label in lowercase prefixed with "is-".
     * If the label is not found, it defaults to returning "is-unknown".
     *
     * @param {string} label - The label to be evaluated and used for generating the class string.
     * @return {string} A CSS class string in the format "is-{label}" or "is-unknown".
     */
    function colorModifierClass(label) {
        return `is-${KNOWN_LABELS.includes(label) ? label.toLowerCase() : 'unknown'}`;
    }

    /**
     * Updates the badge element's CSS classes to set the appropriate color based on the provided label.
     *
     * @param {HTMLElement} badge - The badge element whose color modifier class needs to be updated.
     * @param {string} label - The label used to determine the new color modifier class.
     * @return {void} Does not return any value.
     */
    function setBadgeColor(badge, label) {
        Array.from(badge.classList)
            .filter((c) => c.startsWith('is-') && c !== FIXED_MODIFIER_CLASS)
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

    /**
     * Creates and returns a new badge element.
     *
     * @return {HTMLSpanElement} A span element with a predefined ID and class name.
     */
    function createBadge() {
        const badge = document.createElement('span');
        badge.id = BADGE_ID;
        badge.className = BADGE_CLASS;
        return badge;
    }

    /**
     * Displays a badge with the specified label. If a badge does not exist,
     * it creates one and appends it to the appropriate location in the DOM.
     *
     * Without the unified shell, the classic AEM shell home anchor ("Adobe Experience Manager")
     * is the closest local equivalent of the unified shell's env-labels slot, so the badge goes next to
     * Note: <coral-shell-homeanchor-label> is a custom element tag, not a class - use a tag selector.
     *
     * @param {string} label - The text to be displayed on the badge.
     * @return {void} - Does not return a value.
     */
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

    /**
     * Adds an environment indicator to the user interface based on the current hostname.
     * This indicator displays a label derived from predefined domain labels.
     *
     * @return {void} This method does not return a value.
     */
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
