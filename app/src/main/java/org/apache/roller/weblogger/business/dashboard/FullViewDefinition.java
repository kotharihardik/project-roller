package org.apache.roller.weblogger.business.dashboard;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.roller.weblogger.business.dashboard.collectors.MostActiveWeblogCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.MostCommentedEntryCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.MostStarredBlogCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TopActiveUsersCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TopCategoryCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TotalCommentsCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TotalEntriesCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TotalUsersCollector;
import org.apache.roller.weblogger.business.dashboard.collectors.TotalWeblogsCollector;

public class FullViewDefinition implements DashboardViewDefinition {

    @Override
    public String getViewName() {
        return "full";
    }

    @Override
    public Set<String> getIncludedMetricKeys() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(TotalUsersCollector.KEY);          // 1. Total users
        keys.add(TotalWeblogsCollector.KEY);        // 2. Total weblogs
        keys.add(TotalEntriesCollector.KEY);        // 3. Total entries
        keys.add(TotalCommentsCollector.KEY);       // 4. Total comments
        keys.add(TopCategoryCollector.KEY);         // 5. Top performing category
        keys.add(MostStarredBlogCollector.KEY);     // 6. Most starred blog
        keys.add(TopActiveUsersCollector.KEY);      // 7. Top 3 active users
        keys.add(MostActiveWeblogCollector.KEY);    // 8. Most active weblog by hits
        keys.add(MostCommentedEntryCollector.KEY);  // 9. Most commented entry
        return keys;
    }
}
