/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */
package org.apache.roller.weblogger.ui.struts2.admin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.BugReportManager;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.business.notification.BugReportNotificationService;
import org.apache.roller.weblogger.pojos.BugReport;
import org.apache.roller.weblogger.pojos.GlobalPermission;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.ui.struts2.util.UIAction;
import org.apache.struts2.interceptor.validation.SkipValidation;

/**
 * Admin action for managing all bug reports.
 * URL: /roller-ui/admin/bugReportAdmin         (GET - dashboard)
 * URL: /roller-ui/admin/bugReportAdmin!update  (POST - change status)
 * URL: /roller-ui/admin/bugReportAdmin!delete  (POST - soft-delete)
 */
public class BugReportAdmin extends UIAction {

    private static final Log log = LogFactory.getLog(BugReportAdmin.class);
    private static final int PAGE_SIZE = 25;

    // ---- Filter parameters (bound from request) ----
    private String statusFilter;
    private String severityFilter;
    private int page = 0;

    // ---- Status update parameters ----
    private String reportId;
    private String newStatus;
    private String adminNotes;

    // ---- View data ----
    private List<BugReport> reports = Collections.emptyList();
    private long totalReports   = 0;
    private long openCount      = 0;
    private long inProgressCount= 0;
    private long resolvedCount  = 0;
    private long closedCount    = 0;

    private Map<String, String> statusList;
    private Map<String, String> severityList;

    public BugReportAdmin() {
        this.actionName = "bugReportAdmin";
        this.desiredMenu = "tabbedmenu.admin";
        this.pageTitle = "bugReport.admin.title";
    }

    // ---- Enforce admin-only access (same pattern as UserAdmin, CacheInfo etc.) ----
    @Override
    public List<String> requiredGlobalPermissionActions() {
        return Collections.singletonList(GlobalPermission.ADMIN);
    }

    @Override
    public boolean isWeblogRequired() {
        return false;
    }

    /**
     * GET: Load dashboard with optional filters.
     */
    @Override
    @SkipValidation
    public String execute() {
        loadDashboard();
        return INPUT;
    }

    /**
     * POST: Update status of a single report.
     * Called as bugReportAdmin!update
     */
    public String update() {
        if (StringUtils.isBlank(reportId)) {
            addError("bugReport.error.notFound");
            loadDashboard();
            return INPUT;
        }
        if (StringUtils.isBlank(newStatus)) {
            addError("bugReport.error.invalidTransition");
            loadDashboard();
            return INPUT;
        }

        try {
            BugReportManager mgr =
                    WebloggerFactory.getWeblogger().getBugReportManager();
            User currentUser = getAuthenticatedUser();

            BugReport report = mgr.getBugReport(reportId);
            if (report == null) {
                addError("bugReport.error.notFound");
                loadDashboard();
                return INPUT;
            }

            // Manager handles persistence + fires notification internally
            mgr.updateBugReportStatus(reportId, newStatus, adminNotes, currentUser);

            addMessage("bugReport.status.change.success");

        } catch (WebloggerException e) {
            log.error("Error updating bug report status: reportId=" + reportId, e);
            addError("generic.error.check.logs");
        }

        loadDashboard();
        return INPUT;
    }

    /**
     * POST: Soft-delete a report.
     * Called as bugReportAdmin!delete
     */
    public String delete() {
        if (StringUtils.isBlank(reportId)) {
            addError("bugReport.error.notFound");
            loadDashboard();
            return INPUT;
        }

        try {
            BugReportManager mgr =
                    WebloggerFactory.getWeblogger().getBugReportManager();
            User currentUser = getAuthenticatedUser();

            BugReport report = mgr.getBugReport(reportId);
            if (report == null) {
                addError("bugReport.error.notFound");
                loadDashboard();
                return INPUT;
            }

            // Manager handles: idempotent delete, authorization, and notification
            mgr.removeBugReport(reportId, currentUser);

            addMessage("bugReport.delete.success");

        } catch (WebloggerException e) {
            log.error("Error deleting bug report: reportId=" + reportId, e);
            addError("generic.error.check.logs");
        }

        // Reload dashboard after delete — preserve current filters
        loadDashboard();
        return INPUT;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void loadDashboard() {
        try {
            BugReportManager mgr =
                    WebloggerFactory.getWeblogger().getBugReportManager();

            int offset = page * PAGE_SIZE;

            // Load filtered list
            reports      = mgr.getBugReports(statusFilter, severityFilter, offset, PAGE_SIZE);
            totalReports = mgr.getBugReportCount(statusFilter);

            // Load per-status counts for summary cards
            openCount       = mgr.getBugReportCount(BugReport.STATUS_OPEN);
            inProgressCount = mgr.getBugReportCount(BugReport.STATUS_IN_PROGRESS);
            resolvedCount   = mgr.getBugReportCount(BugReport.STATUS_RESOLVED);
            closedCount     = mgr.getBugReportCount(BugReport.STATUS_CLOSED);

            if (reports == null) {
                reports = Collections.emptyList();
            }

        } catch (WebloggerException e) {
            log.error("Error loading bug report dashboard", e);
            addError("generic.error.check.logs");
            reports = Collections.emptyList();
        }

        populateDropdowns();
    }

    private void populateDropdowns() {
        statusList = new LinkedHashMap<>();
        statusList.put("",                          getText("bugReport.admin.allStatuses"));
        statusList.put(BugReport.STATUS_OPEN,       getText("bugReport.status.OPEN"));
        statusList.put(BugReport.STATUS_IN_PROGRESS,getText("bugReport.status.IN_PROGRESS"));
        statusList.put(BugReport.STATUS_RESOLVED,   getText("bugReport.status.RESOLVED"));
        statusList.put(BugReport.STATUS_CLOSED,     getText("bugReport.status.CLOSED"));

        severityList = new LinkedHashMap<>();
        severityList.put("",                          getText("bugReport.admin.allSeverities"));
        severityList.put(BugReport.SEVERITY_CRITICAL, getText("bugReport.severity.CRITICAL"));
        severityList.put(BugReport.SEVERITY_HIGH,     getText("bugReport.severity.HIGH"));
        severityList.put(BugReport.SEVERITY_MEDIUM,   getText("bugReport.severity.MEDIUM"));
        severityList.put(BugReport.SEVERITY_LOW,      getText("bugReport.severity.LOW"));
    }

    // NOTE: Notifications are fired inside JPABugReportManagerImpl, not here.
    // This method is kept unused for emergency/manual use only.
    @SuppressWarnings("unused")
    private BugReportNotificationService getNotifySvc() {
        return WebloggerFactory.getWeblogger().getBugReportNotificationService();
    }

    // ---- Getters ----

    public List<BugReport> getReports()        { return reports; }
    public long getTotalReports()               { return totalReports; }
    public long getOpenCount()                  { return openCount; }
    public long getInProgressCount()            { return inProgressCount; }
    public long getResolvedCount()              { return resolvedCount; }
    public long getClosedCount()                { return closedCount; }
    public Map<String, String> getStatusList()  { return statusList; }
    public Map<String, String> getSeverityList(){ return severityList; }
    public int getPage()                        { return page; }
    public int getPageSize()                    { return PAGE_SIZE; }
    public boolean isHasMore()                  { return (long)(page + 1) * PAGE_SIZE < totalReports; }
    public String getStatusFilter()             { return statusFilter; }
    public String getSeverityFilter()           { return severityFilter; }
    public String getReportId()                 { return reportId; }
    public String getNewStatus()                { return newStatus; }
    public String getAdminNotes()               { return adminNotes; }

    // ---- Setters ----

    public void setPage(int page)                  { this.page = Math.max(0, page); }
    public void setStatusFilter(String v)          { this.statusFilter   = StringUtils.trimToNull(v); }
    public void setSeverityFilter(String v)        { this.severityFilter = StringUtils.trimToNull(v); }
    public void setReportId(String reportId)       { this.reportId   = reportId; }
    public void setNewStatus(String newStatus)     { this.newStatus  = newStatus; }
    public void setAdminNotes(String adminNotes)   { this.adminNotes = adminNotes; }
}