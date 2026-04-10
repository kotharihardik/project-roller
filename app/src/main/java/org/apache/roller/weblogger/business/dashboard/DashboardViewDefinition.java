package org.apache.roller.weblogger.business.dashboard;

import java.util.Set;

public interface DashboardViewDefinition {

    /**
     * Returns a unique name for this view.
     */
    String getViewName();

    /**
     * Returns the set of metric keys that this view includes.
     */
    Set<String> getIncludedMetricKeys();
}
