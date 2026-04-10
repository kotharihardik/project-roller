package org.apache.roller.weblogger.business.dashboard;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.StarService;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.WeblogManager;
import org.apache.roller.weblogger.business.dashboard.collectors.MostActiveWeblogCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.MostCommentedEntryCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.MostStarredBlogCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TopActiveUsersCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TopCategoryCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TotalCommentsCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TotalEntriesCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TotalUsersCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TotalWeblogsCollector;
import org.apache.roller.weblogger.business.jpa.JPAPersistenceStrategy;

@com.google.inject.Singleton
public class DashboardServiceImpl implements DashboardService {

    private static final Log log = LogFactory.getLog(DashboardServiceImpl.class);

    private final List<MetricCollector> collectors;
    private final DashboardViewFactory viewFactory;

    @com.google.inject.Inject
    public DashboardServiceImpl(
            UserManager userManager,
            WeblogManager weblogManager,
            WeblogEntryManager entryManager,
            StarService starService,
            JPAPersistenceStrategy strategy) {

        // Register all metric collectors
        this.collectors = new ArrayList<>();
        collectors.add(new TotalUsersCollector(userManager));
        collectors.add(new TotalWeblogsCollector(weblogManager));
        collectors.add(new TotalEntriesCollector(entryManager));
        collectors.add(new TotalCommentsCollector(entryManager));
        collectors.add(new TopCategoryCollector(strategy));
        collectors.add(new MostStarredBlogCollector(starService));
        collectors.add(new TopActiveUsersCollector(strategy));
        collectors.add(new MostActiveWeblogCollector(entryManager));
        collectors.add(new MostCommentedEntryCollector(entryManager));

        this.viewFactory = new DashboardViewFactory();

        log.info("DashboardService initialized with " + collectors.size()
                + " collectors and " + viewFactory.getAvailableViewTypes().length + " view definitions");
    }

    @Override
    public DashboardReport generateReport(String viewMode) throws WebloggerException {
        DashboardViewDefinition viewDef = viewFactory.createView(viewMode);

        return new DashboardReportBuilder(collectors)
                .forView(viewDef.getViewName())
                .withMetrics(viewDef.getIncludedMetricKeys())
                .build();
    }

    @Override
    public String[] getAvailableViewModes() {
        return viewFactory.getAvailableViewTypes();
    }
}
