package org.apache.roller.weblogger.business.dashboard.collectors;

import java.util.List;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.dashboard.DashboardMetric;
import org.apache.roller.weblogger.business.dashboard.MetricCollector;
import org.apache.roller.weblogger.pojos.StatCount;

public class MostCommentedEntryCollector implements MetricCollector {

    public static final String KEY = "mostCommentedEntry";
    private final WeblogEntryManager entryManager;

    public MostCommentedEntryCollector(WeblogEntryManager entryManager) {
        this.entryManager = entryManager;
    }

    @Override
    public String getMetricKey() {
        return KEY;
    }

    @Override
    public DashboardMetric collect() throws WebloggerException {
        // Site-wide (null weblog), no date filters, offset 0, limit 1
        List<StatCount> topCommented = entryManager.getMostCommentedWeblogEntries(null, null, null, 0, 1);

        if (topCommented.isEmpty()) {
            return new DashboardMetric(KEY, "Most Commented Entry", "N/A",
                    "No comments found");
        }

        StatCount top = topCommented.get(0);
        return new DashboardMetric(KEY, "Most Commented Entry",
                top.getSubjectNameLong(),
                top.getCount() + " comments | " + top.getWeblogHandle());
    }
}
