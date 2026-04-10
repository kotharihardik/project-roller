package org.apache.roller.weblogger.business.dashboard.collectors;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.dashboard.DashboardMetric;
import org.apache.roller.weblogger.business.dashboard.MetricCollector;

public class TotalCommentsCollector implements MetricCollector {

    public static final String KEY = "totalComments";
    private final WeblogEntryManager entryManager;

    public TotalCommentsCollector(WeblogEntryManager entryManager) {
        this.entryManager = entryManager;
    }

    @Override
    public String getMetricKey() {
        return KEY;
    }

    @Override
    public DashboardMetric collect() throws WebloggerException {
        long count = entryManager.getCommentCount();
        return new DashboardMetric(KEY, "Total Comments", String.valueOf(count));
    }
}
