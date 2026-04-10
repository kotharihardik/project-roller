package org.apache.roller.weblogger.ui.struts2.core;

import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.BugReportManager;
import org.apache.roller.weblogger.business.WebloggerFactory;

import org.apache.roller.weblogger.pojos.BugReport;
import org.apache.roller.weblogger.pojos.User;

import org.apache.roller.weblogger.ui.struts2.util.UIAction;
import org.apache.struts2.interceptor.validation.SkipValidation;
import org.apache.struts2.convention.annotation.AllowedMethods;


/**
 * Action for logged-in users to view their own submitted bug reports.
 * URL: /roller-ui/bugReportList
 */
@AllowedMethods({"execute","deleteReport"})
public class BugReportList extends UIAction {

    private static final Log log = LogFactory.getLog(BugReportList.class);

    private static final int PAGE_SIZE = 20;

    private List<BugReport> reports = Collections.emptyList();
    private long totalReports = 0;

    // Pagination
    private int page = 0;

    // NEW: used for deletion
    private String reportId;

    public BugReportList() {
        this.actionName = "bugReportList";
        this.desiredMenu = "editor";
        this.pageTitle = "bugReport.list.title";
    }

    @Override
    public boolean isWeblogRequired() {
        return false;
    }

    @Override
    @SkipValidation
    public String execute() {

        User currentUser = getAuthenticatedUser();

        if (currentUser == null) {
            addError("error.authentication.required");
            return ERROR;
        }

        try {

            BugReportManager mgr =
                WebloggerFactory.getWeblogger().getBugReportManager();

            int offset = page * PAGE_SIZE;

            reports      = mgr.getBugReportsByUser(currentUser, offset, PAGE_SIZE);
            totalReports = mgr.getBugReportCountByUser(currentUser);

            if (reports == null) {
                reports = Collections.emptyList();
            }

        } catch (WebloggerException e) {

            log.error("Error loading bug reports for user: "
                    + getAuthenticatedUser().getUserName(), e);

            addError("generic.error.check.logs");
        }

        return INPUT;
    }


    /**
     * NEW FUNCTIONALITY
     * Allows user to delete their own bug report
     */
    public String deleteReport() {

        User currentUser = getAuthenticatedUser();

        if (currentUser == null) {
            addError("error.authentication.required");
            return ERROR;
        }

        if (reportId == null) {
            addError("bugReport.error.notFound");
            return execute();
        }

        try {

            BugReportManager mgr =
                WebloggerFactory.getWeblogger().getBugReportManager();

            // Manager already checks owner/admin + notifications
            mgr.removeBugReport(reportId, currentUser);

            addMessage("bugReport.delete.success");

        } catch (WebloggerException e) {

            log.error("Error deleting bug report: " + reportId, e);

            addError("generic.error.check.logs");
        }

        return execute(); // reload list
    }


    // ---- Getters ----

    public List<BugReport> getReports() { return reports; }
    public long getTotalReports() { return totalReports; }
    public int getPage() { return page; }
    public int getPageSize() { return PAGE_SIZE; }
    public boolean isHasMore() { return (long)(page + 1) * PAGE_SIZE < totalReports; }

    public String getReportId() { return reportId; }

    // ---- Setters ----

    public void setPage(int page) { this.page = Math.max(0, page); }

    public void setReportId(String reportId) { this.reportId = reportId; }
}