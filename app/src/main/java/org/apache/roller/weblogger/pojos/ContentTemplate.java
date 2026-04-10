package org.apache.roller.weblogger.pojos;

import org.apache.roller.weblogger.WebloggerException;

public interface ContentTemplate extends ThemeTemplate {
    Weblog getWeblog();
    boolean isRequired();
    boolean isCustom();
    java.util.List<CustomTemplateRendition> getTemplateRenditions();
    void addTemplateRendition(CustomTemplateRendition newRendition);
}