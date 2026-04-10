package org.apache.roller.weblogger.business.dashboard.collectors;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.dashboard.DashboardMetric;
import org.apache.roller.weblogger.business.dashboard.MetricCollector;

public class TotalEntriesCollector implements MetricCollector {

    public static final String KEY = "totalEntries";
    private final WeblogEntryManager entryManager;

    public TotalEntriesCollector(WeblogEntryManager entryManager) {
        this.entryManager = entryManager;
    }

    @Override
    public String getMetricKey() {
        return KEY;
    }

    @Override
    public DashboardMetric collect() throws WebloggerException {
        long count = entryManager.getEntryCount();
        return new DashboardMetric(KEY, "Total Blog Entries", String.valueOf(count));
    }
}
