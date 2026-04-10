package org.apache.roller.weblogger.business.notification.channel;

import org.apache.roller.weblogger.business.notification.model.NotificationEvent;

/**
 * Strategy interface for notification channels.
 * Add new channels (Slack, Teams, SMS) by implementing this interface only.
 * No changes needed to existing code.
 */
public interface NotificationChannel {
    /** Unique channel name e.g. "email", "slack" */
    String getChannelName();

    /** Returns true if this channel is configured and enabled */
    boolean isEnabled();

    /** Send the notification event through this channel */
    void send(NotificationEvent event);
}