package org.apache.roller.weblogger.business.notification.template;

import org.apache.roller.weblogger.business.notification.model.NotificationEvent;
import org.apache.roller.weblogger.pojos.BugReport;
import org.apache.roller.weblogger.pojos.User;

/**
 * Template Method pattern — one method per event x audience combination.
 * Uses EmailBodyBuilder (Builder pattern) internally.
 */
public class EmailTemplateRenderer {

    // -----------------------------------------------------------------------
    // ADMIN templates
    // -----------------------------------------------------------------------

    public String renderAdminCreated(NotificationEvent e) {
        BugReport r = e.getReport();
        String reporter = r.getCreator() != null ? r.getCreator().getUserName() : "Unknown";
        return new EmailBodyBuilder()
            .header("#d9534f", "New Bug Report Submitted",
                    "A new report requires your attention")
            .intro("A new bug report has been submitted by <strong>" + safe(reporter) + "</strong>.")
            .reportTable(r)
            .section("Description",      r.getDescription(),       "#d9534f")
            .section("Steps to Reproduce", r.getStepsToReproduce(), "#5bc0de")
            .footer("New report from " + safe(reporter))
            .build();
    }

    public String renderAdminUpdated(NotificationEvent e) {
        String modifier = actorName(e.getActor());
        return new EmailBodyBuilder()
            .header("#f0ad4e", "&#x270F;&#xFE0F; Bug Report Updated",
                    "A report has been modified")
            .actorBox("Modified by", modifier, "#f0ad4e")
            .reportTable(e.getReport())
            .section("Admin Notes", e.getReport().getAdminNotes(), "#f0ad4e")
            .footer("Updated by " + safe(modifier))
            .build();
    }

    public String renderAdminStatusChanged(NotificationEvent e) {
        String changedBy = actorName(e.getActor());
        return new EmailBodyBuilder()
            .header("#5bc0de", "Bug Report Status Changed",
                    "A report status has been updated")
            .statusFlow(e.getOldStatus(), e.getNewStatus())
            .reportTable(e.getReport())
            .actorBox("Changed by", changedBy, "#5bc0de")
            .section("Admin Notes", e.getReport().getAdminNotes(), "#5bc0de")
            .footer("Status: " + safe(e.getOldStatus()) + " -> " + safe(e.getNewStatus()))
            .build();
    }

    public String renderAdminDeleted(NotificationEvent e) {
        String deletedBy = actorName(e.getActor());
        return new EmailBodyBuilder()
            .header("#777777", "Bug Report Deleted",
                    "A report has been removed")
            .actorBox("Deleted by", deletedBy, "#777777")
            .reportTable(e.getReport())
            .noticeBox(
                "&#x2139;&#xFE0F; <strong>Soft delete:</strong> This report is still visible "
                + "in the admin dashboard under the <strong>Deleted</strong> filter.",
                "#fff8e1", "#ffe082", "#7a5c00")
            .footer("Deleted by " + safe(deletedBy))
            .build();
    }

    // -----------------------------------------------------------------------
    // USER templates
    // -----------------------------------------------------------------------

    public String renderUserCreated(NotificationEvent e) {
        BugReport r = e.getReport();
        String name = r.getCreator() != null ? r.getCreator().getScreenName() : "User";
        return new EmailBodyBuilder()
            .header("#5cb85c", "Bug Report Received",
                    "We have received your report — thank you!")
            .intro("Hi <strong>" + safe(name) + "</strong>, "
                + "thank you for submitting a bug report to Apache Roller. "
                + "Our team has been notified and will review your report shortly.")
            .reportTable(r)
            .noticeBox(
                "<strong>What happens next?</strong><br/>"
                + "You will receive an email update whenever the status of your report changes.",
                "#f0fff4", "#5cb85c", "#2d6a2d")
            .footer("Confirmation")
            .build();
    }

    public String renderUserStatusChanged(NotificationEvent e) {
        BugReport r = e.getReport();
        String name = r.getCreator() != null ? r.getCreator().getScreenName() : "User";
        EmailBodyBuilder b = new EmailBodyBuilder()
            .header("#337ab7", "Update on Your Bug Report",
                    "The status of your report has changed")
            .intro("Hi <strong>" + safe(name) + "</strong>, "
                + "the status of your bug report has been updated by our team.")
            .statusFlow(e.getOldStatus(), e.getNewStatus())
            .reportTable(r);

        // Dynamic status message
        String ns = e.getNewStatus();
        if ("RESOLVED".equals(ns)) {
            b.noticeBox(
                "<strong>Resolved!</strong> Your bug has been fixed. "
                + "Thank you for helping improve Apache Roller!",
                "#f0fff4", "#5cb85c", "#2d6a2d");
        } else if ("IN_PROGRESS".equals(ns)) {
            b.noticeBox(
                "<strong>In Progress</strong> — "
                + "Our team is actively working on your report.",
                "#f0f7ff", "#337ab7", "#1a4a7a");
        } else if ("CLOSED".equals(ns)) {
            b.noticeBox(
                "<strong>Closed</strong> — This report has been closed. "
                + "If the issue persists, please submit a new report.",
                "#f9f9f9", "#ccc", "#555");
        }

        b.section("Note from Admin", r.getAdminNotes(), "#337ab7");
        b.footer("Status: " + safe(e.getOldStatus()) + " -> " + safe(e.getNewStatus()));
        return b.build();
    }

    public String renderUserDeleted(NotificationEvent e) {
        BugReport r = e.getReport();
        String name = r.getCreator() != null ? r.getCreator().getScreenName() : "User";
        return new EmailBodyBuilder()
            .header("#777777", "Your Bug Report Has Been Removed",
                    "A report associated with your account was deleted")
            .intro("Hi <strong>" + safe(name) + "</strong>, "
                + "your bug report has been removed by an administrator.")
            .reportTable(r)
            .noticeBox(
                "&#x2139;&#xFE0F; If you believe this was removed in error, "
                + "please contact the site administrator. "
                + "You are welcome to submit a new report if the issue still exists.",
                "#f9f9f9", "#ddd", "#555")
            .footer("Report removed")
            .build();
    }

    // -----------------------------------------------------------------------
    private static String safe(String s)      { return s != null ? s : ""; }
    private static String actorName(User u)   { return u != null ? u.getUserName() : "Unknown"; }
}