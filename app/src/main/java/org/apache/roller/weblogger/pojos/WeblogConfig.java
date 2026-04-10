package org.apache.roller.weblogger.pojos;

import java.io.Serializable;
import java.util.Objects;

public class WeblogConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String editorTheme;
    private final String locale;
    private final String timeZone;
    private final String defaultPlugins;
    private final String editorPage;
    private final String bannedwordslist;
    private final Boolean allowComments;
    private final Boolean emailComments;
    private final Boolean enableBloggerApi;
    private final Boolean defaultAllowComments;
    private final int defaultCommentDays;
    private final Boolean moderateComments;
    private final int entryDisplayCount;
    private final boolean enableMultiLang;
    private final boolean showAllLangs;
    private final String analyticsCode;
    
    private WeblogConfig(Builder builder) {
        this.editorTheme = builder.editorTheme;
        this.locale = builder.locale;
        this.timeZone = builder.timeZone;
        this.defaultPlugins = builder.defaultPlugins;
        this.editorPage = builder.editorPage;
        this.bannedwordslist = builder.bannedwordslist;
        this.allowComments = builder.allowComments;
        this.emailComments = builder.emailComments;
        this.enableBloggerApi = builder.enableBloggerApi;
        this.defaultAllowComments = builder.defaultAllowComments;
        this.defaultCommentDays = builder.defaultCommentDays;
        this.moderateComments = builder.moderateComments;
        this.entryDisplayCount = builder.entryDisplayCount;
        this.enableMultiLang = builder.enableMultiLang;
        this.showAllLangs = builder.showAllLangs;
        this.analyticsCode = builder.analyticsCode;
    }
    
    public String getEditorTheme() {
        return editorTheme;
    }
    
    public String getLocale() {
        return locale;
    }
    
    public String getTimeZone() {
        return timeZone;
    }
    
    public String getDefaultPlugins() {
        return defaultPlugins;
    }
    
    public String getEditorPage() {
        return editorPage;
    }
    
    public String getBannedwordslist() {
        return bannedwordslist;
    }
    
    public Boolean getAllowComments() {
        return allowComments;
    }
    
    public Boolean getEmailComments() {
        return emailComments;
    }
    
    public Boolean getEnableBloggerApi() {
        return enableBloggerApi;
    }
    
    public Boolean getDefaultAllowComments() {
        return defaultAllowComments;
    }
    
    public int getDefaultCommentDays() {
        return defaultCommentDays;
    }
    
    public Boolean getModerateComments() {
        return moderateComments;
    }
    
    public int getEntryDisplayCount() {
        return entryDisplayCount;
    }
    
    public boolean isEnableMultiLang() {
        return enableMultiLang;
    }
    
    public boolean isShowAllLangs() {
        return showAllLangs;
    }
    
    public String getAnalyticsCode() {
        return analyticsCode;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeblogConfig that = (WeblogConfig) o;
        return defaultCommentDays == that.defaultCommentDays &&
                entryDisplayCount == that.entryDisplayCount &&
                enableMultiLang == that.enableMultiLang &&
                showAllLangs == that.showAllLangs &&
                Objects.equals(editorTheme, that.editorTheme) &&
                Objects.equals(locale, that.locale) &&
                Objects.equals(timeZone, that.timeZone) &&
                Objects.equals(defaultPlugins, that.defaultPlugins) &&
                Objects.equals(editorPage, that.editorPage) &&
                Objects.equals(bannedwordslist, that.bannedwordslist) &&
                Objects.equals(allowComments, that.allowComments) &&
                Objects.equals(emailComments, that.emailComments) &&
                Objects.equals(enableBloggerApi, that.enableBloggerApi) &&
                Objects.equals(defaultAllowComments, that.defaultAllowComments) &&
                Objects.equals(moderateComments, that.moderateComments) &&
                Objects.equals(analyticsCode, that.analyticsCode);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(editorTheme, locale, timeZone, defaultPlugins, editorPage,
                bannedwordslist, allowComments, emailComments, enableBloggerApi,
                defaultAllowComments, defaultCommentDays, moderateComments,
                entryDisplayCount, enableMultiLang, showAllLangs, analyticsCode);
    }
    
    /**
     * Creates a new Builder for constructing WeblogConfig instances.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a Builder pre-populated with values from an existing config.
     */
    public static Builder from(WeblogConfig config) {
        return new Builder()
                .editorTheme(config.editorTheme)
                .locale(config.locale)
                .timeZone(config.timeZone)
                .defaultPlugins(config.defaultPlugins)
                .editorPage(config.editorPage)
                .bannedwordslist(config.bannedwordslist)
                .allowComments(config.allowComments)
                .emailComments(config.emailComments)
                .enableBloggerApi(config.enableBloggerApi)
                .defaultAllowComments(config.defaultAllowComments)
                .defaultCommentDays(config.defaultCommentDays)
                .moderateComments(config.moderateComments)
                .entryDisplayCount(config.entryDisplayCount)
                .enableMultiLang(config.enableMultiLang)
                .showAllLangs(config.showAllLangs)
                .analyticsCode(config.analyticsCode);
    }
    
    /**
     * Builder for WeblogConfig instances.
     */
    public static class Builder {
        private String editorTheme;
        private String locale;
        private String timeZone;
        private String defaultPlugins;
        private String editorPage;
        private String bannedwordslist;
        private Boolean allowComments = Boolean.TRUE;
        private Boolean emailComments = Boolean.FALSE;
        private Boolean enableBloggerApi = Boolean.TRUE;
        private Boolean defaultAllowComments = Boolean.TRUE;
        private int defaultCommentDays = 0;
        private Boolean moderateComments = Boolean.FALSE;
        private int entryDisplayCount = 15;
        private boolean enableMultiLang = false;
        private boolean showAllLangs = true;
        private String analyticsCode;
        
        public Builder editorTheme(String editorTheme) {
            this.editorTheme = editorTheme;
            return this;
        }
        
        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }
        
        public Builder timeZone(String timeZone) {
            this.timeZone = timeZone;
            return this;
        }
        
        public Builder defaultPlugins(String defaultPlugins) {
            this.defaultPlugins = defaultPlugins;
            return this;
        }
        
        public Builder editorPage(String editorPage) {
            this.editorPage = editorPage;
            return this;
        }
        
        public Builder bannedwordslist(String bannedwordslist) {
            this.bannedwordslist = bannedwordslist;
            return this;
        }
        
        public Builder allowComments(Boolean allowComments) {
            this.allowComments = allowComments;
            return this;
        }
        
        public Builder emailComments(Boolean emailComments) {
            this.emailComments = emailComments;
            return this;
        }
        
        public Builder enableBloggerApi(Boolean enableBloggerApi) {
            this.enableBloggerApi = enableBloggerApi;
            return this;
        }
        
        public Builder defaultAllowComments(Boolean defaultAllowComments) {
            this.defaultAllowComments = defaultAllowComments;
            return this;
        }
        
        public Builder defaultCommentDays(int defaultCommentDays) {
            this.defaultCommentDays = defaultCommentDays;
            return this;
        }
        
        public Builder moderateComments(Boolean moderateComments) {
            this.moderateComments = moderateComments;
            return this;
        }
        
        public Builder entryDisplayCount(int entryDisplayCount) {
            this.entryDisplayCount = entryDisplayCount;
            return this;
        }
        
        public Builder enableMultiLang(boolean enableMultiLang) {
            this.enableMultiLang = enableMultiLang;
            return this;
        }
        
        public Builder showAllLangs(boolean showAllLangs) {
            this.showAllLangs = showAllLangs;
            return this;
        }
        
        public Builder analyticsCode(String analyticsCode) {
            this.analyticsCode = analyticsCode;
            return this;
        }
        
        public WeblogConfig build() {
            return new WeblogConfig(this);
        }
    }
}