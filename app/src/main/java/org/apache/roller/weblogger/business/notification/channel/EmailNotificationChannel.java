package org.apache.roller.weblogger.business.notification.channel;

import java.util.List;
import javax.mail.MessagingException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.business.notification.model.NotificationEvent;
import org.apache.roller.weblogger.business.notification.resolver.AdminEmailResolver;
import org.apache.roller.weblogger.business.notification.template.EmailTemplateRenderer;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.pojos.BugReport;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.util.MailUtil;

/**
 * Email channel — Adapter over Roller's MailUtil.
 * Strategy: one of potentially many NotificationChannel implementations.
 */
public class EmailNotificationChannel implements NotificationChannel {

    private static final Log LOG = LogFactory.getLog(EmailNotificationChannel.class);

    private final AdminEmailResolver adminResolver;
    private final EmailTemplateRenderer renderer;

    public EmailNotificationChannel(AdminEmailResolver adminResolver,
                                     EmailTemplateRenderer renderer) {
        this.adminResolver = adminResolver;
        this.renderer      = renderer;
    }

    @Override
    public String getChannelName() { return "email"; }

    @Override
    public boolean isEnabled() {
        String val = WebloggerConfig.getProperty("notification.bugreport.enabled");
        return val == null || "true".equalsIgnoreCase(val.trim());
    }

    @Override
    public void send(NotificationEvent event) {
        if (!isEnabled()) return;

        BugReport report = event.getReport();
        String    from   = getFrom();

        switch (event.getType()) {
            case BUG_CREATED:
                sendToAdmins(from, buildAdminSubject("New Bug Report", report),
                        renderer.renderAdminCreated(event));
                sendToUser(from, report.getCreator(),
                        buildUserSubject("Bug Report Received", report),
                        renderer.renderUserCreated(event));
                break;

            case BUG_UPDATED:
                sendToAdmins(from, buildAdminSubject("Bug Report Updated", report),
                        renderer.renderAdminUpdated(event));
                // notify creator only if someone else updated
                if (report.getCreator() != null && event.getActor() != null
                        && !report.getCreator().getId().equals(event.getActor().getId())) {
                    sendToUser(from, report.getCreator(),
                            buildUserSubject("Your Bug Report Was Updated", report),
                            renderer.renderAdminUpdated(event));
                }
                break;

            case BUG_STATUS_CHANGED:
                sendToAdmins(from, buildAdminSubject("Bug Status Changed", report),
                        renderer.renderAdminStatusChanged(event));
                sendToUser(from, report.getCreator(),
                        buildUserSubject("Status Update on Your Bug Report", report),
                        renderer.renderUserStatusChanged(event));
                break;

            case BUG_DELETED:
                sendToAdmins(from, buildAdminSubject("Bug Report Deleted", report),
                        renderer.renderAdminDeleted(event));
                // notify creator only if admin deleted (not self-delete)
                if (report.getCreator() != null && event.getActor() != null
                        && !report.getCreator().getId().equals(event.getActor().getId())) {
                    sendToUser(from, report.getCreator(),
                            buildUserSubject("Your Bug Report Was Removed", report),
                            renderer.renderUserDeleted(event));
                }
                break;

            default:
                LOG.warn("Unknown event type: " + event.getType());
        }
    }

    private void sendToAdmins(String from, String subject, String html) {
        List<String> adminEmails = adminResolver.resolveAdminEmails();
        if (adminEmails.isEmpty()) {
            LOG.warn("No admin emails found — skipping admin notification");
            return;
        }
        try {
            String[] to = adminEmails.toArray(new String[0]);
            MailUtil.sendHTMLMessage(from, to, null, null, subject, html);
            LOG.info("Admin notification sent [" + subject + "] to " + adminEmails.size() + " admin(s)");
        } catch (MessagingException e) {
            LOG.error("Failed to send admin email: " + subject, e);
        }
    }

    private void sendToUser(String from, User user, String subject, String html) {
        if (user == null || user.getEmailAddress() == null
                || user.getEmailAddress().trim().isEmpty()) {
            LOG.warn("sendToUser: no valid email for user, skipping");
            return;
        }
        try {
            MailUtil.sendHTMLMessage(from, new String[]{user.getEmailAddress()},
                    null, null, subject, html);
            LOG.info("User notification sent to " + user.getUserName());
        } catch (MessagingException e) {
            LOG.error("Failed to send user email to " + user.getUserName(), e);
        }
    }

    private String getFrom() {
        String from = WebloggerConfig.getProperty("notification.bugreport.from");
        if (from == null || from.trim().isEmpty())
            from = WebloggerConfig.getProperty("mail.from.address");
        if (from == null || from.trim().isEmpty())
            from = "noreply@roller.apache.org";
        return from;
    }

    private String buildAdminSubject(String prefix, BugReport r) {
        return "[BugTracker Admin] " + prefix + ": "
                + (r.getTitle() != null ? r.getTitle().replaceAll("[\\r\\n]","") : "");
    }

    private String buildUserSubject(String prefix, BugReport r) {
        return "[BugTracker] " + prefix + ": "
                + (r.getTitle() != null ? r.getTitle().replaceAll("[\\r\\n]","") : "");
    }
}