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

import java.io.Serializable;
import java.util.*;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.plugins.entry.WeblogEntryPlugin;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.config.WebloggerRuntimeConfig;
import org.apache.roller.weblogger.business.BookmarkManager;
import org.apache.roller.weblogger.business.plugins.PluginManager;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.business.WeblogService;
import org.apache.roller.weblogger.business.WeblogServiceImpl;
import org.apache.roller.weblogger.business.themes.ThemeManager;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.pojos.WeblogEntry.PubStatus;
import org.apache.roller.util.UUIDGenerator;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.util.I18nUtils;
import org.apache.roller.weblogger.util.Utilities;


/**
 * Website has many-to-many association with users. Website has one-to-many and
 * one-direction associations with weblog entries, weblog categories, folders and
 * other objects. Use UserManager to create, fetch, update and retrieve websites.
 *
 * @author David M Johnson
 */
public class Weblog implements Serializable {
    
    public static final long serialVersionUID = 206437645033737127L;
    
    private static Log log = LogFactory.getLog(Weblog.class);

    private static final int MAX_ENTRIES = 100;
    
    // Simple properties
    private String  id               = UUIDGenerator.generateUUID();
    private String  handle           = null;
    private String  name             = null;
    private String  tagline          = null;
    private Boolean enableBloggerApi = Boolean.TRUE;
    private String  editorPage       = null;
    private String  bannedwordslist  = null;
    private Boolean allowComments    = Boolean.TRUE;
    private Boolean emailComments    = Boolean.FALSE;
    private String  emailAddress     = null;
    private String  editorTheme      = null;
    private String  locale           = null;
    private String  timeZone         = null;
    private String  defaultPlugins   = null;
    private Boolean visible          = Boolean.TRUE;
    private Boolean active           = Boolean.TRUE;
    private Date    dateCreated      = new java.util.Date();
    private Boolean defaultAllowComments = Boolean.TRUE;
    private int     defaultCommentDays = 0;
    private Boolean moderateComments = Boolean.FALSE;
    private int     entryDisplayCount = 15;
    private Date    lastModified     = new Date();
    private boolean enableMultiLang  = false;
    private boolean showAllLangs     = true;
    private String  iconPath         = null;
    private String  about            = null;
    private String  creator          = null;
    private String  analyticsCode    = null;

    // Associated objects
    private WeblogCategory bloggerCategory = null;

    private Map<String, WeblogEntryPlugin> initializedPlugins = null;

    private List<WeblogCategory> weblogCategories = new ArrayList<>();

    private List<WeblogBookmarkFolder> bookmarkFolders = new ArrayList<>();

    private List<MediaFileDirectory> mediaFileDirectories = new ArrayList<>();

    public Weblog() {
    }
    
    public Weblog(
            String handle,
            String creator,
            String name,
            String desc,
            String email,
            String editorTheme,
            String locale,
            String timeZone) {
        
        this.handle = handle;
        this.creator = creator;
        this.name = name;
        this.tagline = desc;
        this.emailAddress = email;
        this.editorTheme = editorTheme;
        this.locale = locale;
        this.timeZone = timeZone;
    }
    
    //------------------------------------------------------- Good citizenship

    @Override
    public String toString() {
        return  "{" + getId() + ", " + getHandle()
        + ", " + getName() + ", " + getEmailAddress()
        + ", " + getLocale() + ", " + getTimeZone() + "}";
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Weblog)) {
            return false;
        }
        Weblog o = (Weblog)other;
        return new EqualsBuilder()
            .append(getHandle(), o.getHandle()) 
            .isEquals();
    }
    
    @Override
    public int hashCode() { 
        return new HashCodeBuilder()
            .append(getHandle())
            .toHashCode();
    } 
    
    /**
     * Get the Theme object in use by this weblog, or null if no theme selected.
     * 
     * @deprecated Use WeblogService.getTheme() instead. This method will be removed
     *             to reduce coupling and move business logic out of the entity.
     */
    @Deprecated
    public Theme getTheme() {
        try {
            // Delegate to service layer
            WeblogService weblogService = new WeblogServiceImpl(WebloggerFactory.getWeblogger());
            return weblogService.getTheme(this);
        } catch (WebloggerException ex) {
            log.error("Error getting theme for weblog - "+getHandle(), ex);
        }
        return null;
    }

    /**
     * Id of the Website.
     */
    public String getId() {
        return this.id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * Short URL safe string that uniquely identifies the website.
     */
    public String getHandle() {
        return this.handle;
    }
    
    public void setHandle(String handle) {
        this.handle = handle;
    }
    
    /**
     * Name of the Website.
     */
    public String getName() {
        return this.name;
    }
    
    public void setName(String name) {
        this.name = Utilities.removeHTML(name);
    }
    
    /**
     * Description
     *
     */
    public String getTagline() {
        return this.tagline;
    }
    
    public void setTagline(String tagline) {
        this.tagline = Utilities.removeHTML(tagline);
    }
    
    /**
     * Original creator of website.
     */
    public org.apache.roller.weblogger.pojos.User getCreator() {
        try {
            return WebloggerFactory.getWeblogger().getUserManager().getUserByUserName(creator);
        } catch (Exception e) {
            log.error("ERROR fetching user object for username: " + creator, e);
        }
        return null;
    }
    
    /**
     * Username of original creator of website.
     */
    public String getCreatorUserName() {
        return creator;
    }
    
    public void setCreatorUserName(String creatorUserName) {
        creator = creatorUserName;
    }

    public Boolean getEnableBloggerApi() {
        return this.enableBloggerApi;
    }
    
    public void setEnableBloggerApi(Boolean enableBloggerApi) {
        this.enableBloggerApi = enableBloggerApi;
    }
    
    public WeblogCategory getBloggerCategory() { return bloggerCategory; }
    
    public void setBloggerCategory(WeblogCategory bloggerCategory) {
        this.bloggerCategory = bloggerCategory;
    }
    
    public String getEditorPage() {
        return this.editorPage;
    }
    
    public void setEditorPage(String editorPage) {
        this.editorPage = editorPage;
    }
    
    public String getBannedwordslist() {
        return this.bannedwordslist;
    }
    
    public void setBannedwordslist(String bannedwordslist) {
        this.bannedwordslist = bannedwordslist;
    }
    
    public Boolean getAllowComments() {
        return this.allowComments;
    }
    
    public void setAllowComments(Boolean allowComments) {
        this.allowComments = allowComments;
    }
    
    public Boolean getDefaultAllowComments() {
        return defaultAllowComments;
    }
    
    public void setDefaultAllowComments(Boolean defaultAllowComments) {
        this.defaultAllowComments = defaultAllowComments;
    }
    
    public int getDefaultCommentDays() {
        return defaultCommentDays;
    }
    
    public void setDefaultCommentDays(int defaultCommentDays) {
        this.defaultCommentDays = defaultCommentDays;
    }
    
    public Boolean getModerateComments() {
        return moderateComments;
    }
    
    public void setModerateComments(Boolean moderateComments) {
        this.moderateComments = moderateComments;
    }
    
    public Boolean getEmailComments() {
        return this.emailComments;
    }
    
    public void setEmailComments(Boolean emailComments) {
        this.emailComments = emailComments;
    }
    
    public String getEmailAddress() {
        return this.emailAddress;
    }
    
    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }
    
    /**
     * EditorTheme of the Website.
     */
    public String getEditorTheme() {
        return this.editorTheme;
    }
    
    public void setEditorTheme(String editorTheme) {
        this.editorTheme = editorTheme;
    }
    
    /**
     * Locale of the Website.
     */
    public String getLocale() {
        return this.locale;
    }
    
    public void setLocale(String locale) {
        this.locale = locale;
    }
    
    /**
     * Timezone of the Website.
     */
    public String getTimeZone() {
        return this.timeZone;
    }
    
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
    
    public Date getDateCreated() {
        if (dateCreated == null) {
            return null;
        } else {
            return (Date)dateCreated.clone();
        }
    }

    public void setDateCreated(final Date date) {
        if (date != null) {
            dateCreated = (Date)date.clone();
        } else {
            dateCreated = null;
        }
    }

    /**
     * Comma-delimited list of user's default Plugins.
     */
    public String getDefaultPlugins() {
        return defaultPlugins;
    }

    public void setDefaultPlugins(String string) {
        defaultPlugins = string;
    }

    /**
     * Set bean properties based on other bean.
     */
    public void setData(Weblog other) {
        
        this.setId(other.getId());
        this.setName(other.getName());
        this.setHandle(other.getHandle());
        this.setTagline(other.getTagline());
        this.setCreatorUserName(other.getCreatorUserName());
        this.setEnableBloggerApi(other.getEnableBloggerApi());
        this.setBloggerCategory(other.getBloggerCategory());
        this.setEditorPage(other.getEditorPage());
        this.setBannedwordslist(other.getBannedwordslist());
        this.setAllowComments(other.getAllowComments());
        this.setEmailComments(other.getEmailComments());
        this.setEmailAddress(other.getEmailAddress());
        this.setEditorTheme(other.getEditorTheme());
        this.setLocale(other.getLocale());
        this.setTimeZone(other.getTimeZone());
        this.setVisible(other.getVisible());
        this.setDateCreated(other.getDateCreated());
        this.setEntryDisplayCount(other.getEntryDisplayCount());
        this.setActive(other.getActive());
        this.setLastModified(other.getLastModified());
        this.setWeblogCategories(other.getWeblogCategories());
    }
    
    
    /**
     * Parse locale value and instantiate a Locale object,
     * otherwise return default Locale.
     *
     * @return Locale
     */
    public Locale getLocaleInstance() {
        return I18nUtils.toLocale(getLocale());
    }
    
    
    /**
     * Return TimeZone instance for value of timeZone,
     * otherwise return system default instance.
     * @return TimeZone
     */
    public TimeZone getTimeZoneInstance() {
        if (getTimeZone() == null) {
            this.setTimeZone( TimeZone.getDefault().getID() );
        }
        return TimeZone.getTimeZone(getTimeZone());
    }
    
    
    
    public int getEntryDisplayCount() {
        return entryDisplayCount;
    }
    
    public void setEntryDisplayCount(int entryDisplayCount) {
        this.entryDisplayCount = entryDisplayCount;
    }
    
    /**
     * Set to FALSE to disable and hide this weblog from public view.
     */
    public Boolean getVisible() {
        return this.visible;
    }
    
    public void setVisible(Boolean visible) {
        this.visible = visible;
    }
    
    /**
     * Set to FALSE to exclude this weblog from community areas such as the
     * front page and the planet page.
     */
    public Boolean getActive() {
        return active;
    }
    
    public void setActive(Boolean active) {
        this.active = active;
    }
    
    /**
     * Returns true if comment moderation is required by website or config.
     */ 
    public boolean getCommentModerationRequired() { 
        return (getModerateComments()
         || WebloggerRuntimeConfig.getBooleanProperty("users.moderation.required"));
    }
    
    /** No-op */
    public void setCommentModerationRequired(boolean modRequired) {}    

    
    /**
     * The last time any visible part of this weblog was modified.
     * This includes a change to weblog settings, entries, themes, templates, 
     * comments, categories, bookmarks, folders, etc.
     *
     * Pings are explicitly not included because pings do not
     * affect visible changes to a weblog.
     *
     */
    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }
  
    
    /**
     * Is multi-language blog support enabled for this weblog?
     *
     * If false then urls with various locale restrictions should fail.
     */
    public boolean isEnableMultiLang() {
        return enableMultiLang;
    }

    public void setEnableMultiLang(boolean enableMultiLang) {
        this.enableMultiLang = enableMultiLang;
    }
    
    
    /**
     * Should the default weblog view show entries from all languages?
     *
     * If false then the default weblog view only shows entry from the
     * default locale chosen for this weblog.
     */
    public boolean isShowAllLangs() {
        return showAllLangs;
    }

    public void setShowAllLangs(boolean showAllLangs) {
        this.showAllLangs = showAllLangs;
    }
    
    public String getURL() {
        return WebloggerFactory.getWeblogger().getUrlStrategy().getWeblogURL(this, null, false);
    }

    public String getAbsoluteURL() {
        return WebloggerFactory.getWeblogger().getUrlStrategy().getWeblogURL(this, null, true);
    }

    /**
     * The path under the weblog's resources to an icon image.
     */
    public String getIconPath() {
        return iconPath;
    }

    public void setIconPath(String iconPath) {
        this.iconPath = iconPath;
    }

    public String getAnalyticsCode() {
        return analyticsCode;
    }

    public void setAnalyticsCode(String analyticsCode) {
        this.analyticsCode = analyticsCode;
    }

    /**
     * A description for the weblog (its purpose, authors, etc.)
     *
     * This field is meant to hold a paragraph describing the weblog, in contrast
     * to the short sentence or two 'description' attribute meant for blog taglines
     * and HTML header META description tags.
     *
     */
    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = Utilities.removeHTML(about);
    }
    
    
    /**
     * Get initialized plugins for use during rendering process.
     */
    public Map<String, WeblogEntryPlugin> getInitializedPlugins() {
        if (initializedPlugins == null) {
            try {
                Weblogger roller = WebloggerFactory.getWeblogger();
                PluginManager ppmgr = roller.getPluginManager();
                initializedPlugins = ppmgr.getWeblogEntryPlugins(this);
            } catch (Exception e) {
                log.error("ERROR: initializing plugins");
            }
        }
        return initializedPlugins;
    }
    
    /** 
     * Get weblog entry specified by anchor or null if no such entry exists.
     * 
     * @param anchor Weblog entry anchor
     * @return Weblog entry specified by anchor
     * @deprecated Use WeblogService.getWeblogEntry() instead. This method will be removed
     *             to reduce coupling and move business logic out of the entity.
     */
    @Deprecated
    public WeblogEntry getWeblogEntry(String anchor) {
        try {
            WeblogService weblogService = new WeblogServiceImpl(WebloggerFactory.getWeblogger());
            return weblogService.getWeblogEntry(this, anchor);
        } catch (WebloggerException e) {
            log.error("ERROR: getting entry by anchor", e);
        }
        return null;
    }

    /**
     * Get weblog category by name.
     * 
     * @param categoryName Category name or "nil" for default
     * @return WeblogCategory or null if not found
     * @deprecated Use WeblogService.getWeblogCategory() instead. This method will be removed
     *             to reduce coupling and move business logic out of the entity.
     */
    @Deprecated
    public WeblogCategory getWeblogCategory(String categoryName) {
        try {
            WeblogService weblogService = new WeblogServiceImpl(WebloggerFactory.getWeblogger());
            return weblogService.getWeblogCategory(this, categoryName);
        } catch (WebloggerException e) {
            log.error("ERROR: fetching category: " + categoryName, e);
        }
        return null;
    }

    
    /**
     * Get up to 100 most recent published entries in weblog.
     * 
     * @param cat Category name or null for no category restriction
     * @param length Max entries to return (1-100)
     * @return List of weblog entry objects.
     * @deprecated Use WeblogService.getRecentWeblogEntries() instead. This method will be removed
     *             to reduce coupling and move business logic out of the entity.
     */
    @Deprecated
    public List<WeblogEntry> getRecentWeblogEntries(String cat, int length) {
        try {
            WeblogService weblogService = new WeblogServiceImpl(WebloggerFactory.getWeblogger());
            return weblogService.getRecentWeblogEntries(this, cat, length);
        } catch (WebloggerException e) {
            log.error("ERROR: getting recent entries", e);
        }
        return Collections.emptyList();
    }
    
    /**
     * Get up to 100 most recent published entries in weblog.
     * 
     * @param tag Blog entry tag to query by
     * @param length Max entries to return (1-100)
     * @return List of weblog entry objects.
     * @deprecated Use WeblogService.getRecentWeblogEntriesByTag() instead. This method will be removed
     *             to reduce coupling and move business logic out of the entity.
     */
    @Deprecated
    public List<WeblogEntry> getRecentWeblogEntriesByTag(String tag, int length) {
        try {
            WeblogService weblogService = new WeblogServiceImpl(WebloggerFactory.getWeblogger());
            return weblogService.getRecentWeblogEntriesByTag(this, tag, length);
        } catch (WebloggerException e) {
            log.error("ERROR: getting recent entries", e);
        }
        return Collections.emptyList();
    }   
    
    /**
     * Get up to 100 most recent approved and non-spam comments in weblog.
     * 
     * @param length Max entries to return (1-100)
     * @return List of comment objects.
     * @deprecated Use WeblogService.getRecentComments() instead. This method will be removed
     *             to reduce coupling and move business logic out of the entity.
     */
    @Deprecated
    public List<WeblogEntryComment> getRecentComments(int length) {
        try {
            WeblogService weblogService = new WeblogServiceImpl(WebloggerFactory.getWeblogger());
            return weblogService.getRecentComments(this, length);
        } catch (WebloggerException e) {
            log.error("ERROR: getting recent comments", e);
        }
        return Collections.emptyList();
    }

    
    /**
     * Get bookmark folder by name.
     * 
     * @param folderName Name or path of bookmark folder to be returned (null for root)
     * @return Folder object requested.
     * @deprecated Use WeblogService.getBookmarkFolder() instead. This method will be removed
     *             to reduce coupling and move business logic out of the entity.
     */
    @Deprecated
    public WeblogBookmarkFolder getBookmarkFolder(String folderName) {
        try {
            WeblogService weblogService = new WeblogServiceImpl(WebloggerFactory.getWeblogger());
            return weblogService.getBookmarkFolder(this, folderName);
        } catch (WebloggerException re) {
            log.error("ERROR: fetching folder for weblog", re);
        }
        return null;
    }


    /**
     * Get number of hits counted today.
     * 
     * @deprecated Use WeblogService.getTodaysHits() instead. This method will be removed
     *             to reduce coupling and move business logic out of the entity.
     */
    @Deprecated
    public int getTodaysHits() {
        try {
            WeblogService weblogService = new WeblogServiceImpl(WebloggerFactory.getWeblogger());
            return weblogService.getTodaysHits(this);
        } catch (WebloggerException e) {
            log.error("Error getting weblog hit count", e);
        }
        return 0;
    }

    /**
     * Get a list of TagStats objects for the most popular tags
     *
     * @param sinceDays Number of days into past (or -1 for all days)
     * @param length    Max number of tags to return.
     * @return          Collection of WeblogEntryTag objects
     * @deprecated Use WeblogService.getPopularTags() instead. This method will be removed
     *             to reduce coupling and move business logic out of the entity.
     */
    @Deprecated
    public List<TagStat> getPopularTags(int sinceDays, int length) {
        try {
            WeblogService weblogService = new WeblogServiceImpl(WebloggerFactory.getWeblogger());
            return weblogService.getPopularTags(this, sinceDays, length);
        } catch (Exception e) {
            log.error("ERROR: fetching popular tags for weblog " + this.getName(), e);
        }
        return Collections.emptyList();
    }      

    /**
     * Get total comment count for this weblog.
     * 
     * @deprecated Use WeblogService.getCommentCount() instead. This method will be removed
     *             to reduce coupling and move business logic out of the entity.
     */
    @Deprecated
    public long getCommentCount() {
        try {
            WeblogService weblogService = new WeblogServiceImpl(WebloggerFactory.getWeblogger());
            return weblogService.getCommentCount(this);
        } catch (WebloggerException e) {
            log.error("Error getting comment count for weblog " + this.getName(), e);
        }
        return 0;
    }
    
    /**
     * Get total entry count for this weblog.
     * 
     * @deprecated Use WeblogService.getEntryCount() instead. This method will be removed
     *             to reduce coupling and move business logic out of the entity.
     */
    @Deprecated
    public long getEntryCount() {
        try {
            WeblogService weblogService = new WeblogServiceImpl(WebloggerFactory.getWeblogger());
            return weblogService.getEntryCount(this);
        } catch (WebloggerException e) {
            log.error("Error getting entry count for weblog " + this.getName(), e);
        }
        return 0;
    }


    /**
     * Add a category as a child of this category.
     */
    public void addCategory(WeblogCategory category) {

        // make sure category is not null
        if(category == null || category.getName() == null) {
            throw new IllegalArgumentException("Category cannot be null and must have a valid name");
        }

        // make sure we don't already have a category with that name
        if(hasCategory(category.getName())) {
            throw new IllegalArgumentException("Duplicate category name '"+category.getName()+"'");
        }

        // add it to our list of child categories
        getWeblogCategories().add(category);
    }

    public List<WeblogCategory> getWeblogCategories() {
        return weblogCategories;
    }

    public void setWeblogCategories(List<WeblogCategory> cats) {
        this.weblogCategories = cats;
    }

    public boolean hasCategory(String name) {
        for (WeblogCategory cat : getWeblogCategories()) {
            if(name.equals(cat.getName())) {
                return true;
            }
        }
        return false;
    }

    public List<WeblogBookmarkFolder> getBookmarkFolders() {
        return bookmarkFolders;
    }

    public void setBookmarkFolders(List<WeblogBookmarkFolder> bookmarkFolders) {
        this.bookmarkFolders = bookmarkFolders;
    }

    public List<MediaFileDirectory> getMediaFileDirectories() {
        return mediaFileDirectories;
    }

    public void setMediaFileDirectories(List<MediaFileDirectory> mediaFileDirectories) {
        this.mediaFileDirectories = mediaFileDirectories;
    }

    /**
     * Add a bookmark folder to this weblog.
     */
    public void addBookmarkFolder(WeblogBookmarkFolder folder) {

        // make sure folder is not null
        if(folder == null || folder.getName() == null) {
            throw new IllegalArgumentException("Folder cannot be null and must have a valid name");
        }

        // make sure we don't already have a folder with that name
        if(this.hasBookmarkFolder(folder.getName())) {
            throw new IllegalArgumentException("Duplicate folder name '" + folder.getName() + "'");
        }

        // add it to our list of child folder
        getBookmarkFolders().add(folder);
    }

    /**
     * Does this Weblog have a bookmark folder with the specified name?
     *
     * @param name The name of the folder to check for.
     * @return boolean true if exists, false otherwise.
     */
    public boolean hasBookmarkFolder(String name) {
        for (WeblogBookmarkFolder folder : this.getBookmarkFolders()) {
            if(name.equalsIgnoreCase(folder.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indicates whether this weblog contains the specified media file directory
     *
     * @param name directory name
     *
     * @return true if directory is present, false otherwise.
     */
    public boolean hasMediaFileDirectory(String name) {
        for (MediaFileDirectory directory : this.getMediaFileDirectories()) {
            if (directory.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public MediaFileDirectory getMediaFileDirectory(String name) {
        for (MediaFileDirectory dir : this.getMediaFileDirectories()) {
            if (name.equals(dir.getName())) {
                return dir;
            }
        }
        return null;
    }

}