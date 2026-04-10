package org.apache.roller.weblogger.business.url;

import java.util.List;
import org.apache.roller.weblogger.pojos.Weblog;
public interface FeedURLStrategy {
    String getWeblogFeedURL(Weblog weblog, String locale, String type, String format, String category, String term, List<String> tags, boolean excerpts, boolean absolute);
}
