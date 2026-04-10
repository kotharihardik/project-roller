package org.apache.roller.weblogger.business;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.themes.ThemeManager;
import org.apache.roller.weblogger.pojos.*;
import org.apache.roller.weblogger.pojos.WeblogEntry.PubStatus;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class WeblogServiceImpl implements WeblogService {
    
    private static final Log log = LogFactory.getLog(WeblogServiceImpl.class);
    private static final int MAX_ENTRIES = 100;
    
    private final Weblogger weblogger;
    
    public WeblogServiceImpl(Weblogger weblogger) {
        this.weblogger = weblogger;
    }
    
    @Override
    public WeblogEntry getWeblogEntry(Weblog weblog, String anchor) throws WebloggerException {
        WeblogEntryManager wmgr = weblogger.getWeblogEntryManager();
        return wmgr.getWeblogEntryByAnchor(weblog, anchor);
    }
    
    @Override
    public WeblogCategory getWeblogCategory(Weblog weblog, String categoryName) 
            throws WebloggerException {
        WeblogEntryManager wmgr = weblogger.getWeblogEntryManager();
        
        if (categoryName != null && !categoryName.equals("nil")) {
            return wmgr.getWeblogCategoryByName(weblog, categoryName);
        } else {
            // Return first category as default
            if (!weblog.getWeblogCategories().isEmpty()) {
                return weblog.getWeblogCategories().iterator().next();
            }
            return null;
        }
    }
    
    @Override
    public List<WeblogEntry> getRecentWeblogEntries(Weblog weblog, String categoryName, int maxEntries) 
            throws WebloggerException {
        
        String cat = (categoryName != null && "nil".equals(categoryName)) ? null : categoryName;
        int length = Math.min(Math.max(1, maxEntries), MAX_ENTRIES);
        
        if (length < 1) {
            return Collections.emptyList();
        }
        
        WeblogEntryManager wmgr = weblogger.getWeblogEntryManager();
        WeblogEntrySearchCriteria wesc = new WeblogEntrySearchCriteria();
        wesc.setWeblog(weblog);
        wesc.setCatName(cat);
        wesc.setStatus(PubStatus.PUBLISHED);
        wesc.setMaxResults(length);
        
        return wmgr.getWeblogEntries(wesc);
    }
    
    @Override
    public List<WeblogEntry> getRecentWeblogEntriesByTag(Weblog weblog, String tag, int maxEntries) 
            throws WebloggerException {
        
        String cleanTag = (tag != null && "nil".equals(tag)) ? null : tag;
        int length = Math.min(Math.max(1, maxEntries), MAX_ENTRIES);
        
        if (length < 1) {
            return Collections.emptyList();
        }
        
        List<String> tags = (cleanTag != null) ? List.of(cleanTag) : Collections.emptyList();
        
        WeblogEntryManager wmgr = weblogger.getWeblogEntryManager();
        WeblogEntrySearchCriteria wesc = new WeblogEntrySearchCriteria();
        wesc.setWeblog(weblog);
        wesc.setTags(tags);
        wesc.setStatus(PubStatus.PUBLISHED);
        wesc.setMaxResults(length);
        
        return wmgr.getWeblogEntries(wesc);
    }
    
    @Override
    public List<WeblogEntryComment> getRecentComments(Weblog weblog, int maxComments) 
            throws WebloggerException {
        
        int length = Math.min(Math.max(1, maxComments), MAX_ENTRIES);
        
        if (length < 1) {
            return Collections.emptyList();
        }
        
        WeblogEntryManager wmgr = weblogger.getWeblogEntryManager();
        CommentSearchCriteria csc = new CommentSearchCriteria();
        csc.setWeblog(weblog);
        csc.setStatus(WeblogEntryComment.ApprovalStatus.APPROVED);
        csc.setReverseChrono(true);
        csc.setMaxResults(length);
        
        return wmgr.getComments(csc);
    }
    
    @Override
    public WeblogBookmarkFolder getBookmarkFolder(Weblog weblog, String folderName) 
            throws WebloggerException {
        
        BookmarkManager bmgr = weblogger.getBookmarkManager();
        
        if (folderName == null || folderName.equals("nil") || folderName.trim().equals("/")) {
            return bmgr.getDefaultFolder(weblog);
        } else {
            return bmgr.getFolder(weblog, folderName);
        }
    }
    
    @Override
    public int getTodaysHits(Weblog weblog) throws WebloggerException {
        WeblogEntryManager mgr = weblogger.getWeblogEntryManager();
        WeblogHitCount hitCount = mgr.getHitCountByWeblog(weblog);
        
        return (hitCount != null) ? hitCount.getDailyHits() : 0;
    }
    
    @Override
    public List<TagStat> getPopularTags(Weblog weblog, int sinceDays, int maxTags) 
            throws WebloggerException {
        
        Date startDate = null;
        if (sinceDays > 0) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.DATE, -1 * sinceDays);
            startDate = cal.getTime();
        }
        
        WeblogEntryManager wmgr = weblogger.getWeblogEntryManager();
        return wmgr.getPopularTags(weblog, startDate, 0, maxTags);
    }
    
    @Override
    public long getCommentCount(Weblog weblog) throws WebloggerException {
        WeblogEntryManager mgr = weblogger.getWeblogEntryManager();
        return mgr.getCommentCount(weblog);
    }
    
    @Override
    public long getEntryCount(Weblog weblog) throws WebloggerException {
        WeblogEntryManager mgr = weblogger.getWeblogEntryManager();
        return mgr.getEntryCount(weblog);
    }
    
    @Override
    public Theme getTheme(Weblog weblog) throws WebloggerException {
        ThemeManager themeMgr = weblogger.getThemeManager();
        return themeMgr.getTheme(weblog);
    }
    
    @Override
    public void updateWeblogConfig(Weblog weblog, WeblogConfig config) throws WebloggerException {
        if (weblog == null || config == null) {
            throw new WebloggerException("Weblog and config cannot be null");
        }
        
        // Update weblog with new configuration
        weblog.setEditorTheme(config.getEditorTheme());
        weblog.setLocale(config.getLocale());
        weblog.setTimeZone(config.getTimeZone());
        weblog.setDefaultPlugins(config.getDefaultPlugins());
        weblog.setEditorPage(config.getEditorPage());
        weblog.setBannedwordslist(config.getBannedwordslist());
        weblog.setAllowComments(config.getAllowComments());
        weblog.setEmailComments(config.getEmailComments());
        weblog.setEnableBloggerApi(config.getEnableBloggerApi());
        weblog.setDefaultAllowComments(config.getDefaultAllowComments());
        weblog.setDefaultCommentDays(config.getDefaultCommentDays());
        weblog.setModerateComments(config.getModerateComments());
        weblog.setEntryDisplayCount(config.getEntryDisplayCount());
        weblog.setEnableMultiLang(config.isEnableMultiLang());
        weblog.setShowAllLangs(config.isShowAllLangs());
        weblog.setAnalyticsCode(config.getAnalyticsCode());
        
        // Save changes
        weblogger.getWeblogManager().saveWeblog(weblog);
    }
    
    @Override
    public WeblogConfig getWeblogConfig(Weblog weblog) {
        if (weblog == null) {
            throw new IllegalArgumentException("Weblog cannot be null");
        }
        
        return WeblogConfig.builder()
                .editorTheme(weblog.getEditorTheme())
                .locale(weblog.getLocale())
                .timeZone(weblog.getTimeZone())
                .defaultPlugins(weblog.getDefaultPlugins())
                .editorPage(weblog.getEditorPage())
                .bannedwordslist(weblog.getBannedwordslist())
                .allowComments(weblog.getAllowComments())
                .emailComments(weblog.getEmailComments())
                .enableBloggerApi(weblog.getEnableBloggerApi())
                .defaultAllowComments(weblog.getDefaultAllowComments())
                .defaultCommentDays(weblog.getDefaultCommentDays())
                .moderateComments(weblog.getModerateComments())
                .entryDisplayCount(weblog.getEntryDisplayCount())
                .enableMultiLang(weblog.isEnableMultiLang())
                .showAllLangs(weblog.isShowAllLangs())
                .analyticsCode(weblog.getAnalyticsCode())
                .build();
    }
}