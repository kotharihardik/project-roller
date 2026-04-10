package org.apache.roller.weblogger.business.url;

import java.util.List;
import org.apache.roller.weblogger.pojos.Weblog;

public interface WeblogURLStrategy {
    String getWeblogURL(Weblog weblog, String locale, boolean absolute);
    String getWeblogEntryURL(Weblog weblog, String locale, String entryAnchor, boolean absolute);
    String getWeblogCommentsURL(Weblog weblog, String locale, String entryAnchor, boolean absolute);
    String getWeblogCommentURL(Weblog weblog, String locale, String entryAnchor, String timeStamp, boolean absolute);
    String getWeblogCollectionURL(Weblog weblog, String locale, String category, String dateString, List<String> tags, int pageNum, boolean absolute);
    String getWeblogPageURL(Weblog weblog, String locale, String pageLink, String entryAnchor, String category, String dateString, List<String> tags, int pageNum, boolean absolute);
    String getWeblogResourceURL(Weblog weblog, String filePath, boolean absolute);
    String getWeblogRsdURL(Weblog weblog, boolean absolute);
    String getWeblogTagsJsonURL(Weblog weblog, boolean absolute, int pageNum);
}
