package com.adobe.acs.genericlists;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.Self;

import javax.inject.Inject;
import java.util.Locale;

/**
 * @deprecated Compatibility facade for callers that constructed the 1.x implementation class directly. Use
 *             {@link com.adobe.acs.genericlists.api.GenericList} adaptation instead.
 */
@Deprecated(since = "1.3.0", forRemoval = false)
@Model(
        adaptables = Resource.class,
        adapters = GenericList.class,
        resourceType = "acs-genericlists/components/key-value-list")
public final class GenericListImpl extends com.adobe.acs.genericlists.impl.GenericListImpl {

    @Inject
    public GenericListImpl(@Self final Resource listResource) {
        super(listResource);
    }

    /** @deprecated Use the API item returned by resource adaptation. */
    @Deprecated(since = "1.3.0", forRemoval = false)
    public static final class ItemImpl implements Item {
        private final String title;
        private final String value;
        private final ValueMap properties;

        public ItemImpl(final String title, final String value, final ValueMap properties) {
            this.title = title;
            this.value = value;
            this.properties = properties;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getTitle(final Locale locale) {
            if (locale == null || locale.getLanguage().isBlank()) {
                return title;
            }
            final String exact = properties.get("jcr:title." + locale.toString().toLowerCase(Locale.ROOT), String.class);
            if (exact != null) {
                return exact;
            }
            final String language = properties.get("jcr:title." + locale.getLanguage().toLowerCase(Locale.ROOT), String.class);
            return language == null ? title : language;
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}
