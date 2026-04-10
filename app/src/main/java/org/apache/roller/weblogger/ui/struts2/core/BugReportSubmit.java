/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */
package org.apache.roller.weblogger.ui.struts2.core;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.BugReportManager;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.pojos.BugReport;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.ui.struts2.util.UIAction;
import org.apache.struts2.interceptor.validation.SkipValidation;

/**
 * Action for logged-in users to submit a new bug report.
 * URL: /roller-ui/bugReport!submit (POST)
 * URL: /roller-ui/bugReport     (GET - show form)
 */
public class BugReportSubmit extends UIAction {

    private static final Log log = LogFactory.getLog(BugReportSubmit.class);

    // ---- Form fields (bound by Struts2 parameter injection) ----
    private String title;
    private String description;
    private String category;
    private String severity;
    private String pageUrl;
    private String browserInfo;
    private String stepsToReproduce;
    private String screenshotUrl;

    // ---- Data for view (populated in execute()) ----
    private Map<String, String> categoryList;
    private Map<String, String> severityList;

    public BugReportSubmit() {
        // desiredMenu = "core" means this appears in the logged-in user nav
        this.actionName = "bugReport";
        this.desiredMenu = "editor";
        this.pageTitle = "bugReport.title";
    }

    // Any logged-in user can access — no weblog required
    @Override
    public boolean isWeblogRequired() {
        return false;
    }

    /**
     * GET: Show the empty submission form.
     */
    @Override
    @SkipValidation
    public String execute() {
        populateDropdowns();
        return INPUT;
    }

    /**
     * POST: Process the submitted form.
     * Mapped as bugReport!submit in struts.xml
     */
    public String submit() {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            // Should never happen — interceptor enforces login — but guard anyway
            addError("error.authentication.required");
            return ERROR;
        }

        // Validate
        if (!validateForm()) {
            populateDropdowns();
            return INPUT;
        }

        try {
            BugReportManager mgr =
                    WebloggerFactory.getWeblogger().getBugReportManager();

            BugReport report = new BugReport();
            report.setTitle(title.trim());
            report.setDescription(description.trim());
            report.setCategory(category);
            report.setSeverity(severity);
            report.setCreator(currentUser);

            // Optional fields
            if (StringUtils.isNotBlank(pageUrl)) {
                report.setPageUrl(pageUrl.trim());
            }
            if (StringUtils.isNotBlank(browserInfo)) {
                report.setBrowserInfo(browserInfo.trim());
            }
            if (StringUtils.isNotBlank(stepsToReproduce)) {
                report.setStepsToReproduce(stepsToReproduce.trim());
            }
            if (StringUtils.isNotBlank(screenshotUrl)) {
                report.setScreenshotUrl(screenshotUrl.trim());
            }

            // saveBugReport handles: UUID generation, status=OPEN,
            //                        createdAt/updatedAt, and notification
            mgr.saveBugReport(report);

            addMessage("bugReport.submit.success");
            return SUCCESS;

        } catch (WebloggerException e) {
            log.error("Error saving bug report for user: " + getAuthenticatedUser().getUserName(), e);
            addError("generic.error.check.logs");
            populateDropdowns();
            return INPUT;
        }
    }

    /**
     * Validate required fields and length limits.
     * Returns true if form is valid.
     */
    private boolean validateForm() {
        boolean valid = true;

        if (StringUtils.isBlank(title)) {
            addFieldError("title", getText("bugReport.error.titleRequired"));
            valid = false;
        } else if (title.trim().length() > 255) {
            addFieldError("title", getText("bugReport.error.titleTooLong"));
            valid = false;
        }

        if (StringUtils.isBlank(description)) {
            addFieldError("description", getText("bugReport.error.descriptionRequired"));
            valid = false;
        }

        if (StringUtils.isBlank(category)) {
            addFieldError("category", getText("bugReport.error.categoryRequired"));
            valid = false;
        }

        if (StringUtils.isBlank(severity)) {
            addFieldError("severity", getText("bugReport.error.severityRequired"));
            valid = false;
        }

        return valid;
    }

    private void populateDropdowns() {
        // Ordered map so UI always shows in consistent order
        categoryList = new LinkedHashMap<>();
        categoryList.put(BugReport.CATEGORY_BROKEN_LINK,   getText("bugReport.category.BROKEN_LINK"));
        categoryList.put(BugReport.CATEGORY_BROKEN_BUTTON, getText("bugReport.category.BROKEN_BUTTON"));
        categoryList.put(BugReport.CATEGORY_UI_ISSUE,      getText("bugReport.category.UI_ISSUE"));
        categoryList.put(BugReport.CATEGORY_PERFORMANCE,   getText("bugReport.category.PERFORMANCE"));
        categoryList.put(BugReport.CATEGORY_FUNCTIONALITY, getText("bugReport.category.FUNCTIONALITY"));
        categoryList.put(BugReport.CATEGORY_OTHER,         getText("bugReport.category.OTHER"));

        severityList = new LinkedHashMap<>();
        severityList.put(BugReport.SEVERITY_LOW,      getText("bugReport.severity.LOW"));
        severityList.put(BugReport.SEVERITY_MEDIUM,   getText("bugReport.severity.MEDIUM"));
        severityList.put(BugReport.SEVERITY_HIGH,     getText("bugReport.severity.HIGH"));
        severityList.put(BugReport.SEVERITY_CRITICAL, getText("bugReport.severity.CRITICAL"));
    }

    // ---- Getters and Setters (ALL required by Struts2 parameter injection) ----

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

    public String getBrowserInfo() { return browserInfo; }
    public void setBrowserInfo(String browserInfo) { this.browserInfo = browserInfo; }

    public String getStepsToReproduce() { return stepsToReproduce; }
    public void setStepsToReproduce(String stepsToReproduce) { this.stepsToReproduce = stepsToReproduce; }

    public String getScreenshotUrl() { return screenshotUrl; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }

    public Map<String, String> getCategoryList() { return categoryList; }
    public Map<String, String> getSeverityList() { return severityList; }
}