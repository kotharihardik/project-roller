package org.apache.roller.weblogger.business.url;

import org.apache.roller.weblogger.pojos.Weblog;

public interface MediaURLStrategy {
    String getMediaFileURL(Weblog weblog, String fileAnchor, boolean absolute);
    String getMediaFileThumbnailURL(Weblog weblog, String fileAnchor, boolean absolute);
}
