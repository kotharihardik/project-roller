package org.apache.roller.weblogger.business.jpa;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.util.UUIDGenerator;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.BugReportManager;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.business.notification.BugReportNotificationService;
import org.apache.roller.weblogger.pojos.BugReport;
import org.apache.roller.weblogger.pojos.GlobalPermission;
import org.apache.roller.weblogger.pojos.User;


@com.google.inject.Singleton
public class JPABugReportManagerImpl implements BugReportManager {

    private static final Log LOG = LogFactory.getLog(JPABugReportManagerImpl.class);

    private final Weblogger roller;
    private final JPAPersistenceStrategy strategy;
    private final BugReportNotificationService notificationService;

    @Inject
    protected JPABugReportManagerImpl(
            Weblogger roller,
            JPAPersistenceStrategy strategy,
            BugReportNotificationService notificationService) {
        LOG.debug("Instantiating JPA BugReport Manager");
        this.roller              = roller;
        this.strategy            = strategy;
        this.notificationService = notificationService;
    }

    @Override
    public void saveBugReport(BugReport report) throws WebloggerException {
        if (report == null) {
            throw new WebloggerException("saveBugReport: report cannot be null");
        }
        if (report.getCreator() == null) {
            throw new WebloggerException("saveBugReport: report must have a creator");
        }
        if (report.getTitle() == null || report.getTitle().trim().isEmpty()) {
            throw new WebloggerException("saveBugReport: title is required");
        }
        if (report.getDescription() == null || report.getDescription().trim().isEmpty()) {
            throw new WebloggerException("saveBugReport: description is required");
        }

        // Set managed fields
        report.setId(UUIDGenerator.generateUUID());
        report.setStatus(BugReport.STATUS_OPEN);
        report.setCreatedAt(new Date());
        report.setUpdatedAt(new Date());

        // Defaults if not set
        if (report.getCategory() == null) {
            report.setCategory(BugReport.CATEGORY_OTHER);
        }
        if (report.getSeverity() == null) {
            report.setSeverity(BugReport.SEVERITY_MEDIUM);
        }

        try {
            strategy.store(report);
            strategy.flush();
            LOG.debug("Bug report saved: " + report.getId());
        } catch (Exception e) {
            throw new WebloggerException("Failed to save bug report", e);
        }

        // Fire notification AFTER successful persist
        try {
            notificationService.notifyBugCreated(report);
        } catch (Exception e) {
            // Notification failure must NEVER roll back the save
            LOG.error("Notification failed after saveBugReport " + report.getId(), e);
        }
    }

    @Override
    public void updateBugReport(BugReport report, User modifier) throws WebloggerException {
        if (report == null) {
            throw new WebloggerException("updateBugReport: report cannot be null");
        }
        if (modifier == null) {
            throw new WebloggerException("updateBugReport: modifier cannot be null");
        }

        // Verify the report actually exists
        BugReport existing = getBugReport(report.getId());
        if (existing == null) {
            throw new WebloggerException(
                    "updateBugReport: report not found with id=" + report.getId());
        }
        if (BugReport.STATUS_DELETED.equals(existing.getStatus())) {
            throw new WebloggerException(
                    "updateBugReport: cannot update a deleted report id=" + report.getId());
        }

        // Authorize: only creator or admin can update
        boolean isAdmin  = isAdminUser(modifier);
        boolean isOwner  = existing.getCreator() != null
                && existing.getCreator().getId().equals(modifier.getId());
        if (!isAdmin && !isOwner) {
            throw new WebloggerException(
                    "updateBugReport: user '" + modifier.getUserName()
                    + "' is not authorized to update report " + report.getId());
        }

        // Set managed fields (do NOT allow caller to change id/creator/createdAt)
        report.setUpdatedAt(new Date());
        report.setLastModifiedBy(modifier);

        // Preserve original creator and createdAt
        report.setCreator(existing.getCreator());
        report.setCreatedAt(existing.getCreatedAt());

        try {
            strategy.store(report);
            strategy.flush();
            LOG.debug("Bug report updated: " + report.getId());
        } catch (Exception e) {
            throw new WebloggerException("Failed to update bug report", e);
        }

        try {
            notificationService.notifyBugUpdated(report, modifier);
        } catch (Exception e) {
            LOG.error("Notification failed after updateBugReport " + report.getId(), e);
        }
    }

    @Override
    public void updateBugReportStatus(String reportId, String newStatus,
                                       String adminNotes, User modifier)
            throws WebloggerException {

        if (reportId == null || reportId.trim().isEmpty()) {
            throw new WebloggerException("updateBugReportStatus: reportId is required");
        }
        if (newStatus == null || newStatus.trim().isEmpty()) {
            throw new WebloggerException("updateBugReportStatus: newStatus is required");
        }
        if (modifier == null) {
            throw new WebloggerException("updateBugReportStatus: modifier is required");
        }

        // Validate status value
        if (!isValidStatus(newStatus)) {
            throw new WebloggerException(
                    "updateBugReportStatus: invalid status value '" + newStatus + "'");
        }

        BugReport report = getBugReport(reportId);
        if (report == null) {
            throw new WebloggerException(
                    "updateBugReportStatus: report not found id=" + reportId);
        }
        if (BugReport.STATUS_DELETED.equals(report.getStatus())) {
            throw new WebloggerException(
                    "updateBugReportStatus: cannot change status of a deleted report");
        }

        // Only admins can change status
        if (!isAdminUser(modifier)) {
            throw new WebloggerException(
                    "updateBugReportStatus: only admins can change report status");
        }

        String oldStatus = report.getStatus();

        // Apply changes
        report.setStatus(newStatus);
        report.setLastModifiedBy(modifier);
        report.setUpdatedAt(new Date());

        if (adminNotes != null && !adminNotes.trim().isEmpty()) {
            report.setAdminNotes(adminNotes);
        }

        // Set resolvedAt when transitioning into RESOLVED or CLOSED
        if ((BugReport.STATUS_RESOLVED.equals(newStatus)
                || BugReport.STATUS_CLOSED.equals(newStatus))
                && report.getResolvedAt() == null) {
            report.setResolvedAt(new Date());
        }

        // Clear resolvedAt if re-opening
        if (BugReport.STATUS_OPEN.equals(newStatus)
                || BugReport.STATUS_IN_PROGRESS.equals(newStatus)) {
            report.setResolvedAt(null);
        }

        try {
            strategy.store(report);
            strategy.flush();
            LOG.debug("Bug report status changed: " + reportId
                    + " [" + oldStatus + " -> " + newStatus + "]");
        } catch (Exception e) {
            throw new WebloggerException("Failed to update bug report status", e);
        }

        try {
            notificationService.notifyBugStatusChanged(report, oldStatus, newStatus);
        } catch (Exception e) {
            LOG.error("Notification failed after updateBugReportStatus " + reportId, e);
        }
    }

    @Override
    public void removeBugReport(String reportId, User requestingUser)
            throws WebloggerException {

        if (reportId == null || reportId.trim().isEmpty()) {
            throw new WebloggerException("removeBugReport: reportId is required");
        }
        if (requestingUser == null) {
            throw new WebloggerException("removeBugReport: requestingUser is required");
        }

        BugReport report = getBugReport(reportId);
        if (report == null) {
            // Already gone - idempotent, just log
            LOG.warn("removeBugReport: report not found id=" + reportId + ", nothing to do");
            return;
        }
        if (BugReport.STATUS_DELETED.equals(report.getStatus())) {
            LOG.warn("removeBugReport: report already deleted id=" + reportId);
            return;
        }

        // Authorize: only creator or admin can delete
        boolean isAdmin = isAdminUser(requestingUser);
        boolean isOwner = report.getCreator() != null
                && report.getCreator().getId().equals(requestingUser.getId());
        if (!isAdmin && !isOwner) {
            throw new WebloggerException(
                    "removeBugReport: user '" + requestingUser.getUserName()
                    + "' is not authorized to delete report " + reportId);
        }

        // Soft-delete
        report.setStatus(BugReport.STATUS_DELETED);
        report.setUpdatedAt(new Date());
        report.setLastModifiedBy(requestingUser);

        try {
            strategy.store(report);
            strategy.flush();
            LOG.debug("Bug report soft-deleted: " + reportId);
        } catch (Exception e) {
            throw new WebloggerException("Failed to delete bug report", e);
        }

        try {
            notificationService.notifyBugDeleted(report, requestingUser);
        } catch (Exception e) {
            LOG.error("Notification failed after removeBugReport " + reportId, e);
        }
    }


    @Override
    public BugReport getBugReport(String id) throws WebloggerException {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        try {
            TypedQuery<BugReport> query =
                    strategy.getNamedQuery("BugReport.getById", BugReport.class);
            query.setParameter(1, id);
            List<BugReport> results = query.getResultList();
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            throw new WebloggerException("Failed to get bug report by id=" + id, e);
        }
    }

    @Override
    public List<BugReport> getBugReportsByUser(User user, int offset, int length)
            throws WebloggerException {
        if (user == null) {
            return Collections.emptyList();
        }
        try {
            TypedQuery<BugReport> query =
                    strategy.getNamedQuery("BugReport.getByUser", BugReport.class);
            query.setParameter(1, user);
            if (offset > 0)  query.setFirstResult(offset);
            if (length > 0)  query.setMaxResults(length);
            return query.getResultList();
        } catch (Exception e) {
            throw new WebloggerException(
                    "Failed to get bug reports for user=" + user.getUserName(), e);
        }
    }

    @Override
    public List<BugReport> getBugReports(String statusFilter, String severityFilter,
                                          int offset, int length)
            throws WebloggerException {
        try {
            List<BugReport> results;

            if (statusFilter != null && severityFilter != null) {
                TypedQuery<BugReport> q = strategy.getNamedQuery(
                        "BugReport.getByStatusAndSeverity", BugReport.class);
                q.setParameter(1, statusFilter);
                q.setParameter(2, severityFilter);
                applyPagination(q, offset, length);
                results = q.getResultList();

            } else if (statusFilter != null) {
                TypedQuery<BugReport> q = strategy.getNamedQuery(
                        "BugReport.getByStatus", BugReport.class);
                q.setParameter(1, statusFilter);
                applyPagination(q, offset, length);
                results = q.getResultList();

            } else if (severityFilter != null) {
                TypedQuery<BugReport> q = strategy.getNamedQuery(
                        "BugReport.getBySeverity", BugReport.class);
                q.setParameter(1, severityFilter);
                applyPagination(q, offset, length);
                results = q.getResultList();

            } else {
                TypedQuery<BugReport> q = strategy.getNamedQuery(
                        "BugReport.getAll", BugReport.class);
                applyPagination(q, offset, length);
                results = q.getResultList();
            }

            return results != null ? results : Collections.emptyList();

        } catch (Exception e) {
            throw new WebloggerException(
                    "Failed to get bug reports [status=" + statusFilter
                    + ", severity=" + severityFilter + "]", e);
        }
    }

    @Override
    public long getBugReportCount(String statusFilter) throws WebloggerException {
        try {
            TypedQuery<Long> query;
            if (statusFilter != null) {
                query = strategy.getNamedQuery("BugReport.countByStatus", Long.class);
                query.setParameter(1, statusFilter);
            } else {
                query = strategy.getNamedQuery("BugReport.countAll", Long.class);
            }
            Long count = query.getSingleResult();
            return count != null ? count : 0L;
        } catch (Exception e) {
            throw new WebloggerException(
                    "Failed to count bug reports [status=" + statusFilter + "]", e);
        }
    }

    @Override
    public long getBugReportCountByUser(User user) throws WebloggerException {
        if (user == null) return 0L;
        try {
            TypedQuery<Long> query =
                    strategy.getNamedQuery("BugReport.countByUser", Long.class);
            query.setParameter(1, user);
            Long count = query.getSingleResult();
            return count != null ? count : 0L;
        } catch (Exception e) {
            throw new WebloggerException(
                    "Failed to count bug reports for user=" + user.getUserName(), e);
        }
    }

    @Override
    public long getOpenBugCount() throws WebloggerException {
        return getBugReportCount(BugReport.STATUS_OPEN);
    }

    @Override
    public long getResolvedBugCount() throws WebloggerException {
        return getBugReportCount(BugReport.STATUS_RESOLVED);
    }

    @Override
    public void release() {
        // No resources to release in this implementation
        LOG.debug("JPABugReportManagerImpl.release() called");
    }


    private boolean isAdminUser(User user) {
        if (user == null) return false;
        try {
            GlobalPermission adminPerm =
                    new GlobalPermission(Collections.singletonList(GlobalPermission.ADMIN));
            return roller.getUserManager().checkPermission(adminPerm, user);
        } catch (WebloggerException e) {
            LOG.warn("isAdminUser: could not check permission for user '"
                    + user.getUserName() + "': " + e.getMessage());
            return false;
        }
    }

    private boolean isValidStatus(String status) {
        return BugReport.STATUS_OPEN.equals(status)
                || BugReport.STATUS_IN_PROGRESS.equals(status)
                || BugReport.STATUS_RESOLVED.equals(status)
                || BugReport.STATUS_CLOSED.equals(status)
                || BugReport.STATUS_DELETED.equals(status);
    }

    private void applyPagination(TypedQuery<?> query, int offset, int length) {
        if (offset > 0) query.setFirstResult(offset);
        if (length > 0) query.setMaxResults(length);
    }
}