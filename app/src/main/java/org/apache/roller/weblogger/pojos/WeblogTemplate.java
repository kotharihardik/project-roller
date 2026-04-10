/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.pojos;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.roller.util.UUIDGenerator;
import org.apache.roller.weblogger.WebloggerException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * POJO that represents a single user defined template page.
 *
 * This template wraps a base ThemeTemplate and adds content-specific behavior
 * including association with a specific Weblog.
 */
public class WeblogTemplate implements ContentTemplate, Serializable {

    public static final long serialVersionUID = -613737191638263428L;
    public static final String DEFAULT_PAGE = "Weblog";

    private static final Set<String> requiredTemplates = Set.of("Weblog", "_day");

    // The base template this content template wraps
    private ThemeTemplate baseTemplate;

    // Content-specific attributes (mutable for backward compatibility)
    private String id = null;
    private Weblog weblog = null;
    private ComponentType action = null;
    private String name = null;
    private String description = null;
    private String link = null;
    private Date lastModified = null;
    private boolean hidden = false;
    private boolean navbar = false;
    private String outputContentType = null;
    private List<CustomTemplateRendition> templateRenditions = new ArrayList<>();

    // Constructors
    public WeblogTemplate() {}

    public WeblogTemplate(ThemeTemplate baseTemplate, Weblog weblog) {
        this.baseTemplate = baseTemplate;
        this.weblog = weblog;
        // Initialize from base template if provided
        if (baseTemplate != null) {
            this.action = baseTemplate.getAction();
            this.name = baseTemplate.getName();
            this.description = baseTemplate.getDescription();
            this.link = baseTemplate.getLink();
            this.lastModified = baseTemplate.getLastModified();
            this.hidden = baseTemplate.isHidden();
            this.navbar = baseTemplate.isNavbar();
            this.outputContentType = baseTemplate.getOutputContentType();
        }
    }

    // Delegate ThemeTemplate methods to local values, falling back to base template
    @Override
    public String getId() {
        // For WeblogTemplate, we generate our own ID
        if (id == null) {
            id = UUIDGenerator.generateUUID();
        }
        return id;
    }

    @Override
    public String getName() {
        return name != null ? name : (baseTemplate != null ? baseTemplate.getName() : null);
    }

    @Override
    public String getDescription() {
        return description != null ? description : (baseTemplate != null ? baseTemplate.getDescription() : null);
    }

    @Override
    public Date getLastModified() {
        return lastModified != null ? lastModified : (baseTemplate != null ? baseTemplate.getLastModified() : null);
    }

    @Override
    public String getOutputContentType() {
        return outputContentType != null ? outputContentType : (baseTemplate != null ? baseTemplate.getOutputContentType() : null);
    }

    @Override
    public TemplateRendition getTemplateRendition(CustomTemplateRendition.RenditionType desiredType) throws WebloggerException {
        // First check content-specific renditions
        for (CustomTemplateRendition rnd : templateRenditions) {
            if (rnd.getType().equals(desiredType)) {
                return rnd;
            }
        }

        // Fall back to base template
        TemplateRendition baseRendition = baseTemplate != null ? baseTemplate.getTemplateRendition(desiredType) : null;

        // If we have a base rendition but it's not a CustomTemplateRendition,
        // wrap it in a CustomTemplateRendition for backward compatibility
        if (baseRendition != null && !(baseRendition instanceof CustomTemplateRendition)) {
            CustomTemplateRendition customRendition = new CustomTemplateRendition();
            customRendition.setType(desiredType);
            customRendition.setTemplate(baseRendition.getTemplate());
            customRendition.setTemplateLanguage(baseRendition.getTemplateLanguage());
            customRendition.setWeblogTemplate(this);
            return customRendition;
        }

        return baseRendition;
    }

    @Override
    public ComponentType getAction() {
        return action != null ? action : (baseTemplate != null ? baseTemplate.getAction() : null);
    }

    @Override
    public String getLink() {
        return link != null ? link : (baseTemplate != null ? baseTemplate.getLink() : null);
    }

    @Override
    public boolean isHidden() {
        return hidden || (baseTemplate != null ? baseTemplate.isHidden() : false);
    }

    @Override
    public boolean isNavbar() {
        return navbar || (baseTemplate != null ? baseTemplate.isNavbar() : false);
    }

    // ContentTemplate specific methods
    @Override
    public Weblog getWeblog() {
        return this.weblog;
    }

    public void setWeblog(Weblog website) {
        this.weblog = website;
    }

    @Override
    public boolean isRequired() {
       /*
        * this is kind of hacky right now, but it's like that so we can be
        * reasonably flexible while we migrate old blogs which may have some
        * pretty strange customizations.
        *
        * my main goal starting now is to prevent further deviations from the
        * standardized templates as we move forward.
        *
        * eventually, the required flag should probably be stored in the db
        * and possibly applicable to any template.
        */
        return (requiredTemplates.contains(getName()) || "Weblog".equals(getLink()));
    }

    @Override
    public boolean isCustom() {
        return ComponentType.CUSTOM.equals(getAction()) && !isRequired();
    }

    @Override
    public List<CustomTemplateRendition> getTemplateRenditions() {
        return templateRenditions;
    }

    public void setTemplateRenditions(List<CustomTemplateRendition> templateRenditions) {
        this.templateRenditions = templateRenditions;
    }

    @Override
    public void addTemplateRendition(CustomTemplateRendition newRendition) {
        if (hasTemplateRendition(newRendition)) {
            throw new IllegalArgumentException("Rendition type '" + newRendition.getType()
                    + " for template '" + this.getName() + "' already exists.");
        }
        templateRenditions.add(newRendition);
    }

    public boolean hasTemplateRendition(CustomTemplateRendition proposed) {
        for (CustomTemplateRendition rnd : templateRenditions) {
            if(rnd.getType().equals(proposed.getType())) {
                return true;
            }
        }
        return false;
    }

    // Getters and setters for base template (for backward compatibility during migration)
    public ThemeTemplate getBaseTemplate() {
        return baseTemplate;
    }

    public void setBaseTemplate(ThemeTemplate baseTemplate) {
        this.baseTemplate = baseTemplate;
    }

    // Setter methods for backward compatibility - modify local values
    public void setId(String id) {
        this.id = id;
    }

    public void setAction(ComponentType action) {
        this.action = action;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void setNavbar(boolean navbar) {
        this.navbar = navbar;
    }

    public void setOutputContentType(String outputContentType) {
        this.outputContentType = outputContentType;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    //------------------------------------------------------- Good citizenship

    @Override
    public String toString() {
        return "{" + getId() + ", " + getName() + ", " + getLink() + "}";
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof WeblogTemplate)) {
            return false;
        }
        WeblogTemplate o = (WeblogTemplate)other;
        return new EqualsBuilder()
            .append(getName(), o.getName())
            .append(getWeblog(), o.getWeblog())
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(getName())
            .append(getWeblog())
            .toHashCode();
    }

}
