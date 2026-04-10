package org.apache.roller.weblogger.business;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.pojos.*;

import java.util.Date;
import java.util.List;

public interface WeblogService {
    
    WeblogEntry getWeblogEntry(Weblog weblog, String anchor) throws WebloggerException;
    WeblogCategory getWeblogCategory(Weblog weblog, String categoryName) throws WebloggerException;
    List<WeblogEntry> getRecentWeblogEntries(Weblog weblog, String categoryName, int maxEntries) throws WebloggerException;
    List<WeblogEntry> getRecentWeblogEntriesByTag(Weblog weblog, String tag, int maxEntries) throws WebloggerException;
    List<WeblogEntryComment> getRecentComments(Weblog weblog, int maxComments) throws WebloggerException;
    WeblogBookmarkFolder getBookmarkFolder(Weblog weblog, String folderName) throws WebloggerException;
    int getTodaysHits(Weblog weblog) throws WebloggerException;
    List<TagStat> getPopularTags(Weblog weblog, int sinceDays, int maxTags) throws WebloggerException;
    long getCommentCount(Weblog weblog) throws WebloggerException;
    long getEntryCount(Weblog weblog) throws WebloggerException;
    Theme getTheme(Weblog weblog) throws WebloggerException;
    void updateWeblogConfig(Weblog weblog, WeblogConfig config) throws WebloggerException;
    WeblogConfig getWeblogConfig(Weblog weblog);
}