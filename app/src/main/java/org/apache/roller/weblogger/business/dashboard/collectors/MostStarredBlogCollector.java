package org.apache.roller.weblogger.business.dashboard.collectors;

import java.util.List;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.StarService;
import org.apache.roller.weblogger.business.dashboard.DashboardMetric;
import org.apache.roller.weblogger.business.dashboard.MetricCollector;
import org.apache.roller.weblogger.pojos.StatCount;

public class MostStarredBlogCollector implements MetricCollector {

    public static final String KEY = "mostStarredBlog";
    private final StarService starService;

    public MostStarredBlogCollector(StarService starService) {
        this.starService = starService;
    }

    @Override
    public String getMetricKey() {
        return KEY;
    }

    @Override
    public DashboardMetric collect() throws WebloggerException {
        List<StatCount> topEntries = starService.getTopStarredEntries(0, 1);

        if (topEntries.isEmpty()) {
            return new DashboardMetric(KEY, "Most Starred Blog Entry", "N/A",
                    "No starred entries yet");
        }

        StatCount top = topEntries.get(0);
        return new DashboardMetric(KEY, "Most Starred Blog Entry",
                top.getSubjectNameLong(),
                top.getCount() + " stars | " + top.getWeblogHandle());
    }
}
