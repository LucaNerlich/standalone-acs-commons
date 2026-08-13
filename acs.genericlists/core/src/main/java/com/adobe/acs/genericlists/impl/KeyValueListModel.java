package com.adobe.acs.genericlists.impl;

import com.adobe.acs.genericlists.api.GenericList;
import com.adobe.acs.genericlists.api.GenericListValidationIssue;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.Self;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Narrow HTL presentation model for the Key/Value List component.
 *
 * <p>This deliberately replaces the former attempt to instantiate the GenericList interface directly from HTL.
 * It is bound only to the canonical component resource type and does not make unrelated resources adaptable.</p>
 */
@Model(
        adaptables = Resource.class,
        resourceType = GenericListImpl.RT_KEY_VALUE_LIST,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public final class KeyValueListModel {

    @Self
    private Resource resource;

    private GenericList list;

    @PostConstruct
    private void initialize() {
        if (resource != null && resource.isResourceType(GenericListImpl.RT_KEY_VALUE_LIST)) {
            list = new GenericListImpl(resource);
        }
    }

    public List<GenericList.Item> getItems() {
        return list == null ? List.of() : list.getItems();
    }

    public List<GenericListValidationIssue> getValidationIssues() {
        return list == null ? List.of() : list.getValidationIssues();
    }

    public boolean isRenderable() {
        return list != null;
    }
}
