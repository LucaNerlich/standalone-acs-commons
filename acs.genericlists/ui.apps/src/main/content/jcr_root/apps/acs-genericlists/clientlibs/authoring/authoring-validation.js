(function ($, $document) {
    "use strict";

    var registry = $(window).adaptTo("foundation-registry");
    if (!registry) {
        return;
    }

    function valueOf(element) {
        return ($(element).val() || "").trim();
    }

    function bcp47(value) {
        // A pragmatic BCP-47 guard. Server-side GenericListSchema remains the source of truth.
        return /^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$/.test(value);
    }

    function hasDuplicate(elements, current, normalizer) {
        var value = normalizer(valueOf(current));
        if (!value) {
            return false;
        }
        var count = 0;
        elements.each(function () {
            if (normalizer(valueOf(this)) === value) {
                count++;
            }
        });
        return count > 1;
    }

    registry.register("foundation.validation.validator", {
        selector: "[data-validation~='acs-genericlists-title']",
        validate: function (element) {
            var value = valueOf(element);
            if (!value) {
                return "A title is required.";
            }
            if (value.length > 255) {
                return "A title may contain at most 255 characters.";
            }
        }
    });

    registry.register("foundation.validation.validator", {
        selector: "[data-validation~='acs-genericlists-value']",
        validate: function (element) {
            var value = valueOf(element);
            if (!value) {
                return "A value is required.";
            }
            if (value.length > 255) {
                return "A value may contain at most 255 characters.";
            }
            var dialog = $(element).closest(".cq-dialog");
            if (hasDuplicate(dialog.find("[data-validation~='acs-genericlists-value']"), element, function (input) {
                return input;
            })) {
                return "Each list item must use a unique value.";
            }
        }
    });

    registry.register("foundation.validation.validator", {
        selector: "[data-validation~='acs-genericlists-locale']",
        validate: function (element) {
            var value = valueOf(element);
            if (!value) {
                return;
            }
            if (!bcp47(value)) {
                return "Use a BCP-47 locale such as de, de-CH, or zh-Hant-TW.";
            }
            var item = $(element).closest("coral-multifield-item").first();
            if (!item.length) {
                return;
            }
            var localeFields = item.find("[data-validation~='acs-genericlists-locale']");
            if (localeFields.length > 1 && hasDuplicate(localeFields, element, function (input) {
                return input.replace(/_/g, "-").toLowerCase();
            })) {
                return "A localized title may define each locale only once.";
            }
        }
    });

    // Revalidate duplicate fields whenever authors edit a value. This produces immediate feedback while the
    // server-side schema still protects imports and direct repository writes.
    $document.on("input change", "[data-validation~='acs-genericlists-value'], [data-validation~='acs-genericlists-locale']", function () {
        var field = $(this).adaptTo("foundation-field");
        if (field && field.setInvalid) {
            field.setInvalid(false);
        }
    });
}(Granite.$, Granite.$(document)));
