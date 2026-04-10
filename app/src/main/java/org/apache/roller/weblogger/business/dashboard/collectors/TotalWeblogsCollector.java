package org.apache.roller.weblogger.business.dashboard.collectors;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WeblogManager;
import org.apache.roller.weblogger.business.dashboard.DashboardMetric;
import org.apache.roller.weblogger.business.dashboard.MetricCollector;

public class TotalWeblogsCollector implements MetricCollector {

    public static final String KEY = "totalWeblogs";
    private final WeblogManager weblogManager;

    public TotalWeblogsCollector(WeblogManager weblogManager) {
        this.weblogManager = weblogManager;
    }

    @Override
    public String getMetricKey() {
        return KEY;
    }

    @Override
    public DashboardMetric collect() throws WebloggerException {
        long count = weblogManager.getWeblogCount();
        return new DashboardMetric(KEY, "Total Weblogs", String.valueOf(count));
    }
}
