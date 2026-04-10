package org.apache.roller.weblogger.business.notification;

import com.google.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.mail.MessagingException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.business.notification.channel.EmailNotificationChannel;
import org.apache.roller.weblogger.business.notification.model.NotificationEvent;
import org.apache.roller.weblogger.business.notification.model.NotificationEvent.EventType;
import org.apache.roller.weblogger.business.notification.resolver.AdminEmailResolver;
import org.apache.roller.weblogger.business.notification.template.EmailTemplateRenderer;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.pojos.BugReport;
import org.apache.roller.weblogger.pojos.GlobalPermission;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.util.MailUtil;

@com.google.inject.Singleton
public class BugReportNotificationServiceImpl implements BugReportNotificationService {

    private static final Log LOG =
        LogFactory.getLog(BugReportNotificationServiceImpl.class);
    private static final SimpleDateFormat DATE_FMT =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final UserManager          userManager;
    private final NotificationDispatcher dispatcher;

    @Inject
    public BugReportNotificationServiceImpl(UserManager userManager) {
        this.userManager = userManager;

        // Wire up dispatcher with channels
        AdminEmailResolver  resolver  = new AdminEmailResolver(userManager);
        EmailTemplateRenderer renderer = new EmailTemplateRenderer();
        EmailNotificationChannel emailChannel =
            new EmailNotificationChannel(resolver, renderer);

        this.dispatcher = new NotificationDispatcher();
        this.dispatcher.registerChannel(emailChannel);
        // Future: dispatcher.registerChannel(new SlackNotificationChannel(...));
        // Future: dispatcher.registerChannel(new TeamsNotificationChannel(...));

        LOG.info("BugReportNotificationServiceImpl initialized with dispatcher");
    }

    // Public API — called from BugReportAdmin action / BugReportManagerImpl
    @Override
    public void notifyBugCreated(BugReport report) {
        if (report == null || report.getCreator() == null) return;
        NotificationEvent event = new NotificationEvent.Builder()
            .type(EventType.BUG_CREATED)
            .report(report)
            .actor(report.getCreator())
            .build();
        dispatcher.dispatch(event);
    }

    @Override
    public void notifyBugUpdated(BugReport report, User modifier) {
        if (report == null || modifier == null) return;
        NotificationEvent event = new NotificationEvent.Builder()
            .type(EventType.BUG_UPDATED)
            .report(report)
            .actor(modifier)
            .build();
        dispatcher.dispatch(event);
    }

    @Override
    public void notifyBugStatusChanged(BugReport report,
                                        String oldStatus, String newStatus) {
        if (report == null || oldStatus == null || newStatus == null) return;
        NotificationEvent event = new NotificationEvent.Builder()
            .type(EventType.BUG_STATUS_CHANGED)
            .report(report)
            .oldStatus(oldStatus)
            .newStatus(newStatus)
            .build();
        dispatcher.dispatch(event);
    }

    @Override
    public void notifyBugDeleted(BugReport report, User deletedBy) {
        if (report == null || deletedBy == null) return;
        NotificationEvent event = new NotificationEvent.Builder()
            .type(EventType.BUG_DELETED)
            .report(report)
            .actor(deletedBy)
            .build();
        dispatcher.dispatch(event);
    }

    // Legacy HTML helpers kept for backward compat (used by buildReportSummaryTable)
    private String safe(String s)     { return s != null ? s : ""; }
    private boolean notBlank(String s){ return s != null && !s.trim().isEmpty(); }
    private String sanitize(String s) {
        return s != null ? s.replaceAll("[\\r\\n]"," ").trim() : "";
    }
    private String formatDate(Date d) {
        return d != null ? DATE_FMT.format(d) : "N/A";
    }
}