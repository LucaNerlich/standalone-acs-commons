package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.GenericList;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

import java.io.StringWriter;
import java.util.Locale;

/** Shared resource adaptation and JSON serialization routines for the public delivery contracts. */
@SuppressWarnings("deprecation")
final class GenericListJsonSupport {

    private GenericListJsonSupport() {
    }

    static GenericList fromResource(final Resource resource) {
        if (resource == null) {
            return null;
        }
        // Prefer the modern API adapter so every delivery surface shares its deterministic validation contract.
        final com.adobe.acs.genericlists.api.GenericList apiList =
                resource.adaptTo(com.adobe.acs.genericlists.api.GenericList.class);
        if (apiList instanceof GenericList compatibleList) {
            return compatibleList;
        }
        final GenericList directList = resource.adaptTo(GenericList.class);
        if (directList != null) {
            return directList;
        }

        final PageManager pageManager = resource.getResourceResolver().adaptTo(PageManager.class);
        if (pageManager == null) {
            return null;
        }
        Page page = pageManager.getPage(resource.getPath());
        if (page == null) {
            page = pageManager.getContainingPage(resource);
        }
        if (page == null) {
            return null;
        }
        final GenericList pageList = page.adaptTo(GenericList.class);
        if (pageList != null) {
            return pageList;
        }
        final Resource listResource = GenericListAdapterFactory.getListResource(page.getContentResource());
        return listResource == null ? null : new GenericListImpl(listResource);
    }

    static String toListJson(final GenericList list, final Locale locale) {
        final StringWriter output = new StringWriter();
        final JSONWriter json = new JSONWriter(output);
        try {
            json.array();
            for (final GenericList.Item item : list.getItems()) {
                json.object()
                        .key("value").value(item.getValue())
                        .key("text").value(title(item, locale))
                        .endObject();
            }
            json.endArray();
            return output.toString();
        } catch (JSONException ex) {
            throw new IllegalStateException("Unable to serialize Generic List", ex);
        }
    }

    static String toOptionsJson(final GenericList list, final Locale locale) {
        final StringWriter output = new StringWriter();
        final JSONWriter json = new JSONWriter(output);
        try {
            json.object().key("options").array();
            for (final GenericList.Item item : list.getItems()) {
                final String title = title(item, locale);
                json.object()
                        .key("text").value(title)
                        .key("title").value(title)
                        .key("value").value(item.getValue())
                        .endObject();
            }
            json.endArray().endObject();
            return output.toString();
        } catch (JSONException ex) {
            throw new IllegalStateException("Unable to serialize Generic List options", ex);
        }
    }

    private static String title(final GenericList.Item item, final Locale locale) {
        return locale == null ? item.getTitle() : item.getTitle(locale);
    }
}
