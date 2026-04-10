package org.apache.roller.weblogger.business.dashboard;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.roller.weblogger.business.dashboard.collectors.TopCategoryCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TotalUsersCollector;

public class MinimalistViewDefinition implements DashboardViewDefinition {

    @Override
    public String getViewName() {
        return "minimalist";
    }

    @Override
    public Set<String> getIncludedMetricKeys() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(TotalUsersCollector.KEY);        // Total Users
        keys.add(TopCategoryCollector.KEY);        // Top Category
        return keys;
    }
}
