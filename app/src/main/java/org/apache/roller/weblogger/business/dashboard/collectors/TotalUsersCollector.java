package org.apache.roller.weblogger.business.dashboard.collectors;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.business.dashboard.DashboardMetric;
import org.apache.roller.weblogger.business.dashboard.MetricCollector;

public class TotalUsersCollector implements MetricCollector {

    public static final String KEY = "totalUsers";
    private final UserManager userManager;

    public TotalUsersCollector(UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    public String getMetricKey() {
        return KEY;
    }

    @Override
    public DashboardMetric collect() throws WebloggerException {
        long count = userManager.getUserCount();
        return new DashboardMetric(KEY, "Total Users", String.valueOf(count));
    }
}
