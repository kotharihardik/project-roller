/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.roller.weblogger.pojos;

import java.io.Serializable;
import java.util.Date;

/**
 * Bug report POJO - plain Java bean with no JPA annotations.
 * Persistence mapping defined in BugReport.orm.xml
 */
public class BugReport implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---- Status Constants ----
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_DELETED = "DELETED";

    // ---- Category Constants ----
    public static final String CATEGORY_BROKEN_LINK = "BROKEN_LINK";
    public static final String CATEGORY_BROKEN_BUTTON = "BROKEN_BUTTON";
    public static final String CATEGORY_UI_ISSUE = "UI_ISSUE";
    public static final String CATEGORY_PERFORMANCE = "PERFORMANCE";
    public static final String CATEGORY_FUNCTIONALITY = "FUNCTIONALITY";
    public static final String CATEGORY_OTHER = "OTHER";

    // ---- Severity Constants ----
    public static final String SEVERITY_LOW = "LOW";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    // ---- Fields ----
    // All private with getters/setters - mapping in XML

    private String id;
    private User creator;
    private String title;
    private String description;
    private String category = CATEGORY_OTHER;
    private String severity = SEVERITY_MEDIUM;
    private String status = STATUS_OPEN;
    private String pageUrl;
    private String browserInfo;
    private String stepsToReproduce;
    private String screenshotUrl;
    private String adminNotes;
    private User lastModifiedBy;
    private Date createdAt;
    private Date updatedAt;
    private Date resolvedAt;
    private Integer optLock = 0; // Optimistic locking version

    // ---- Constructors ----

    public BugReport() {
    }

    public BugReport(String id, User creator, String title, String description) {
        this.id = id;
        this.creator = creator;
        this.title = title;
        this.description = description;
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    // ---- Getters and Setters ----

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public String getBrowserInfo() {
        return browserInfo;
    }

    public void setBrowserInfo(String browserInfo) {
        this.browserInfo = browserInfo;
    }

    public String getStepsToReproduce() {
        return stepsToReproduce;
    }

    public void setStepsToReproduce(String stepsToReproduce) {
        this.stepsToReproduce = stepsToReproduce;
    }

    public String getScreenshotUrl() {
        return screenshotUrl;
    }

    public void setScreenshotUrl(String screenshotUrl) {
        this.screenshotUrl = screenshotUrl;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }

    public User getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(User lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Date getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Date resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Integer getOptLock() {
        return optLock;
    }

    public void setOptLock(Integer optLock) {
        this.optLock = optLock;
    }

    // ---- Object Overrides ----

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BugReport other = (BugReport) obj;
        return id != null && id.equals(other.id);
    }

    @Override
    public String toString() {
        return "BugReport{id=" + id + ", title='" + title + "', status=" + status + "}";
    }
}