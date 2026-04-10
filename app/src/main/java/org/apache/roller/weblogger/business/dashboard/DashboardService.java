package org.apache.roller.weblogger.business.dashboard;

import org.apache.roller.weblogger.WebloggerException;

public interface DashboardService {

    /**
     * Generates a dashboard report for the specified view mode.
     * 
     * @param viewMode the view mode ("minimalist" or "full")
     * @return a DashboardReport containing the requested metrics
     * @throws WebloggerException if report generation fails
     */
    DashboardReport generateReport(String viewMode) throws WebloggerException;

    /**
     * Returns the list of available view mode names.
     */
    String[] getAvailableViewModes();
}
