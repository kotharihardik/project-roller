package org.apache.roller.weblogger.business.dashboard.collectors;

import java.util.List;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.dashboard.DashboardMetric;
import org.apache.roller.weblogger.business.dashboard.MetricCollector;
import org.apache.roller.weblogger.pojos.WeblogHitCount;

public class MostActiveWeblogCollector implements MetricCollector {

    public static final String KEY = "mostActiveWeblog";
    private final WeblogEntryManager entryManager;

    public MostActiveWeblogCollector(WeblogEntryManager entryManager) {
        this.entryManager = entryManager;
    }

    @Override
    public String getMetricKey() {
        return KEY;
    }

    @Override
    public DashboardMetric collect() throws WebloggerException {
        List<WeblogHitCount> hotWeblogs = entryManager.getHotWeblogs(1, 0, 1);

        if (hotWeblogs.isEmpty()) {
            return new DashboardMetric(KEY, "Most Active Weblog (by Hits)", "N/A",
                    "No hit data available");
        }

        WeblogHitCount top = hotWeblogs.get(0);
        String handle = top.getWeblog() != null ? top.getWeblog().getHandle() : "unknown";
        String name = top.getWeblog() != null ? top.getWeblog().getName() : "Unknown";

        return new DashboardMetric(KEY, "Most Active Weblog (by Hits)",
                name,
                top.getDailyHits() + " daily hits | handle: " + handle);
    }
}
