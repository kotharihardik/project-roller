package org.apache.roller.weblogger.business;

import java.util.List;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.pojos.BugReport;
import org.apache.roller.weblogger.pojos.User;

/**
 * Manager interface for BugReport CRUD and query operations.
 */
public interface BugReportManager {

    /**
     * Save a new bug report.
     * Manager sets: id (UUID), timestamps, status=OPEN.
     * Triggers notifications to admins and user.
     * 
     * @param report The bug report to save
     * @throws WebloggerException on persistence error
     */
    void saveBugReport(BugReport report) throws WebloggerException;

    /**
     * Update an existing bug report's details.
     * Manager sets: updatedAt, lastModifiedBy.
     * Triggers admin notification only (not user if they're the modifier).
     * 
     * @param report The bug report with updated fields
     * @param modifier The user making the change
     * @throws WebloggerException on persistence error or not found
     */
    void updateBugReport(BugReport report, User modifier) throws WebloggerException;

    /**
     * Change status of a bug report with optional admin notes.
     * Validates status transition.
     * Manager sets: updatedAt, resolvedAt (if status=RESOLVED), lastModifiedBy.
     * Triggers notifications to admins and user.
     * 
     * @param reportId The bug report ID
     * @param newStatus The new status value
     * @param adminNotes Optional admin notes (can be null)
     * @param modifier The user making the change
     * @throws WebloggerException on invalid transition or not found
     */
    void updateBugReportStatus(String reportId, String newStatus,
                                String adminNotes, User modifier) throws WebloggerException;

    /**
     * Soft-delete a bug report (sets status to DELETED).
     * Checks ownership: requestingUser must be creator OR admin.
     * Triggers notifications.
     * 
     * @param reportId The bug report ID
     * @param requestingUser The user requesting deletion
     * @throws WebloggerException on authorization failure or not found
     */
    void removeBugReport(String reportId, User requestingUser) throws WebloggerException;

    /**
     * Retrieve a single bug report by ID.
     * 
     * @param id The bug report ID
     * @return BugReport or null if not found
     * @throws WebloggerException on query error
     */
    BugReport getBugReport(String id) throws WebloggerException;

    /**
     * Get bug reports created by a specific user.
     * Excludes DELETED reports.
     * 
     * @param user The creator user
     * @param offset Pagination offset (0-based)
     * @param length Max results to return
     * @return List of bug reports (may be empty)
     * @throws WebloggerException on query error
     */
    List<BugReport> getBugReportsByUser(User user, int offset, int length) 
        throws WebloggerException;

    /**
     * Get bug reports with optional filters (admin dashboard).
     * 
     * @param statusFilter Status to filter by, or null for all non-deleted.
     *                     Use "DELETED" to retrieve deleted reports.
     * @param severityFilter Severity to filter by, or null for all.
     * @param offset Pagination offset
     * @param length Max results
     * @return List of bug reports
     * @throws WebloggerException on query error
     */
    List<BugReport> getBugReports(String statusFilter, String severityFilter,
                                   int offset, int length) throws WebloggerException;

    /**
     * Count bug reports matching status filter.
     * 
     * @param statusFilter Status to count, or null for all non-deleted
     * @return Count
     * @throws WebloggerException on query error
     */
    long getBugReportCount(String statusFilter) throws WebloggerException;

    /**
     * Count bug reports created by a specific user (excludes DELETED).
     * 
     * @param user The creator user
     * @return Count
     * @throws WebloggerException on query error
     */
    long getBugReportCountByUser(User user) throws WebloggerException;

    /**
     * Count currently open bug reports.
     * For admin dashboard summary.
     * 
     * @return Count of reports with status=OPEN
     * @throws WebloggerException on query error
     */
    long getOpenBugCount() throws WebloggerException;

    /**
     * Count resolved bug reports.
     * For admin dashboard summary.
     * 
     * @return Count of reports with status=RESOLVED
     * @throws WebloggerException on query error
     */
    long getResolvedBugCount() throws WebloggerException;

    /**
     * Release resources. Called on manager shutdown.
     */
    void release();
}