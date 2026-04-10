package org.apache.roller.weblogger.business.dashboard.collectors;

import java.util.List;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.dashboard.DashboardMetric;
import org.apache.roller.weblogger.business.dashboard.MetricCollector;
import org.apache.roller.weblogger.business.jpa.JPAPersistenceStrategy;
import org.apache.roller.weblogger.pojos.WeblogEntry.PubStatus;

import jakarta.persistence.Query;

public class TopCategoryCollector implements MetricCollector {

    public static final String KEY = "topCategory";
    private final JPAPersistenceStrategy strategy;

    public TopCategoryCollector(JPAPersistenceStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public String getMetricKey() {
        return KEY;
    }

    @Override
    public DashboardMetric collect() throws WebloggerException {
        // Efficient aggregate query: group entries by category, order by count DESC, take top 1
        String jpql =
                "SELECT c.name, COUNT(e) " +
                "FROM WeblogEntry e JOIN e.category c " +
                "WHERE e.status = ?1 " +
                "GROUP BY c.name " +
                "ORDER BY COUNT(e) DESC";

        Query q = strategy.getDynamicQuery(jpql);
        q.setParameter(1, PubStatus.PUBLISHED);
        q.setMaxResults(1);

        @SuppressWarnings("unchecked")
        List<Object[]> results = q.getResultList();

        if (results.isEmpty()) {
            return new DashboardMetric(KEY, "Top Category", "N/A", "No published entries found");
        }

        Object[] top = results.get(0);
        String categoryName = (String) top[0];
        long entryCount = ((Number) top[1]).longValue();

        return new DashboardMetric(KEY, "Top Category",
                categoryName, entryCount + " published entries");
    }
}
