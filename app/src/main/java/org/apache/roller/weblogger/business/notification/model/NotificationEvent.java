package org.apache.roller.weblogger.business.notification.model;

import org.apache.roller.weblogger.pojos.BugReport;
import org.apache.roller.weblogger.pojos.User;

/**
 * Immutable event object passed to all notification channels.
 * Built via Builder pattern.
 */
public class NotificationEvent {

    public enum EventType {
        BUG_CREATED,
        BUG_UPDATED,
        BUG_STATUS_CHANGED,
        BUG_DELETED
    }

    private final EventType type;
    private final BugReport report;
    private final User actor;          // who triggered the event
    private final String oldStatus;    // for STATUS_CHANGED only
    private final String newStatus;    // for STATUS_CHANGED only

    private NotificationEvent(Builder b) {
        this.type      = b.type;
        this.report    = b.report;
        this.actor     = b.actor;
        this.oldStatus = b.oldStatus;
        this.newStatus = b.newStatus;
    }

    public EventType  getType()      { return type; }
    public BugReport  getReport()    { return report; }
    public User       getActor()     { return actor; }
    public String     getOldStatus() { return oldStatus; }
    public String     getNewStatus() { return newStatus; }

    /** Builder */
    public static class Builder {
        private EventType type;
        private BugReport report;
        private User      actor;
        private String    oldStatus;
        private String    newStatus;

        public Builder type(EventType t)       { this.type = t; return this; }
        public Builder report(BugReport r)     { this.report = r; return this; }
        public Builder actor(User u)           { this.actor = u; return this; }
        public Builder oldStatus(String s)     { this.oldStatus = s; return this; }
        public Builder newStatus(String s)     { this.newStatus = s; return this; }

        public NotificationEvent build() {
            if (type == null || report == null)
                throw new IllegalStateException("type and report are required");
            return new NotificationEvent(this);
        }
    }
}