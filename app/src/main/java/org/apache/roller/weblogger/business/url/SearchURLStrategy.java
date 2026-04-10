package org.apache.roller.weblogger.business.url;

import org.apache.roller.weblogger.pojos.Weblog;
public interface SearchURLStrategy {
    String getWeblogSearchURL(Weblog weblog, String locale, String query, String category, int pageNum, boolean absolute);
    String getOpenSearchSiteURL();
    String getOpenSearchWeblogURL(String weblogHandle);
    String getWeblogSearchFeedURLTemplate(Weblog weblog);
    String getWeblogSearchPageURLTemplate(Weblog weblog);
    String getWeblogURL(Weblog weblog, String locale, boolean absolute);
    String getWeblogEntryURL(Weblog weblog, String locale, String entryAnchor, boolean absolute);
}
