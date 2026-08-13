package com.adobe.acs.genericlists;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

import java.io.StringWriter;

@SuppressWarnings("deprecation")
final class GenericListJsonSupport {

    private GenericListJsonSupport() {
    }

    static GenericList fromResource(final Resource resource) {
        if (resource == null) {
            return null;
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
        return page == null ? null : page.adaptTo(GenericList.class);
    }

    static String toListJson(final GenericList list) {
        final StringWriter output = new StringWriter();
        final JSONWriter json = new JSONWriter(output);
        try {
            json.array();
            for (final GenericList.Item item : list.getItems()) {
                json.object()
                        .key("value").value(item.getValue())
                        .key("text").value(item.getTitle())
                        .endObject();
            }
            json.endArray();
            return output.toString();
        } catch (JSONException ex) {
            throw new IllegalStateException("Unable to serialize Generic List", ex);
        }
    }

    static String toOptionsJson(final GenericList list) {
        final StringWriter output = new StringWriter();
        final JSONWriter json = new JSONWriter(output);
        try {
            json.object().key("options").array();
            for (final GenericList.Item item : list.getItems()) {
                json.object()
                        .key("text").value(item.getTitle())
                        .key("title").value(item.getTitle())
                        .key("value").value(item.getValue())
                        .endObject();
            }
            json.endArray().endObject();
            return output.toString();
        } catch (JSONException ex) {
            throw new IllegalStateException("Unable to serialize Generic List options", ex);
        }
    }
}
