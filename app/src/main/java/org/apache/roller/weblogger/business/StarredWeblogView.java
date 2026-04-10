package org.apache.roller.weblogger.business;

import java.sql.Timestamp;

import org.apache.roller.weblogger.pojos.Weblog;

public class StarredWeblogView {

    private final Weblog weblog;
    private final Timestamp lastPublishedPostTime;

    public StarredWeblogView(Weblog weblog, Timestamp lastPublishedPostTime) {
        this.weblog = weblog;
        this.lastPublishedPostTime = lastPublishedPostTime;
    }

    public Weblog getWeblog() {
        return weblog;
    }

    public Timestamp getLastPublishedPostTime() {
        return lastPublishedPostTime;
    }
}
