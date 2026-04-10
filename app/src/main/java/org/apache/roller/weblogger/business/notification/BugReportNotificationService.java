package org.apache.roller.weblogger.business.notification;

import org.apache.roller.weblogger.pojos.BugReport;
import org.apache.roller.weblogger.pojos.User;

/**
 * Service for sending bug report email notifications.
 * Uses existing Roller MailUtil pattern (sendTextMessage / sendHTMLMessage).
 */
public interface BugReportNotificationService {

    /**
     * Notify admins + send confirmation to reporter when new bug is created.
     */
    void notifyBugCreated(BugReport report);

    /**
     * Notify admins. Notify creator only if someone ELSE updated.
     */
    void notifyBugUpdated(BugReport report, User modifier);

    /**
     * Notify admins + creator always when status changes.
     */
    void notifyBugStatusChanged(BugReport report, String oldStatus, String newStatus);

    /**
     * Notify admins. Notify creator only if someone ELSE deleted.
     */
    void notifyBugDeleted(BugReport report, User deletedBy);
}