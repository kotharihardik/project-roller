package org.apache.roller.weblogger.ui.struts2.admin;

import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.business.dashboard.DashboardReport;
import org.apache.roller.weblogger.business.dashboard.DashboardService;
import org.apache.roller.weblogger.pojos.GlobalPermission;
import org.apache.roller.weblogger.ui.struts2.util.UIAction;

public class SiteSummaryDashboard extends UIAction {

    private static final Log log = LogFactory.getLog(SiteSummaryDashboard.class);

    // The current view mode ("minimalist" or "full")
    private String viewMode = "full";

    // The generated report containing metrics
    private DashboardReport report;

    // Available view modes for the toggle UI
    private String[] availableViewModes;

    public SiteSummaryDashboard() {
        this.actionName = "siteSummaryDashboard";
        this.desiredMenu = "admin";
        this.pageTitle = "siteSummaryDashboard.title";
    }

    @Override
    public List<String> requiredGlobalPermissionActions() {
        return Collections.singletonList(GlobalPermission.ADMIN);
    }

    @Override
    public boolean isWeblogRequired() {
        return false;
    }

    @Override
    public void myPrepare() {
        try {
            DashboardService dashboardService =
                    WebloggerFactory.getWeblogger().getDashboardService();
            this.availableViewModes = dashboardService.getAvailableViewModes();
        } catch (Exception e) {
            log.error("Error preparing dashboard", e);
            this.availableViewModes = new String[]{"minimalist", "full"};
        }
    }

    @Override
    public String execute() {
        try {
            DashboardService dashboardService =
                    WebloggerFactory.getWeblogger().getDashboardService();
            this.report = dashboardService.generateReport(viewMode);
        } catch (Exception e) {
            log.error("Error generating dashboard report for view: " + viewMode, e);
            addError("siteSummaryDashboard.error.generation");
        }
        return SUCCESS;
    }

    /**
     * Action method to toggle the view mode.
     * Invoked when the user clicks the toggle button.
     */
    public String toggleView() {
        // Toggle between minimalist and full
        if ("minimalist".equals(viewMode)) {
            viewMode = "full";
        } else {
            viewMode = "minimalist";
        }
        return execute();
    }

    // --- Getters and Setters ---

    public String getViewMode() {
        return viewMode;
    }

    public void setViewMode(String viewMode) {
        this.viewMode = viewMode;
    }

    public DashboardReport getReport() {
        return report;
    }

    public void setReport(DashboardReport report) {
        this.report = report;
    }

    public String[] getAvailableViewModes() {
        return availableViewModes;
    }
}
