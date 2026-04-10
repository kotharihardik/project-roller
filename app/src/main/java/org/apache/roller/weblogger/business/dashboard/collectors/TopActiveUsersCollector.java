package org.apache.roller.weblogger.business.dashboard.collectors;

import java.util.List;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.dashboard.DashboardMetric;
import org.apache.roller.weblogger.business.dashboard.MetricCollector;
import org.apache.roller.weblogger.business.jpa.JPAPersistenceStrategy;
import org.apache.roller.weblogger.pojos.WeblogEntry.PubStatus;

import jakarta.persistence.Query;

public class TopActiveUsersCollector implements MetricCollector {

    public static final String KEY = "topActiveUsers";
    private final JPAPersistenceStrategy strategy;

    public TopActiveUsersCollector(JPAPersistenceStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public String getMetricKey() {
        return KEY;
    }

    @Override
    public DashboardMetric collect() throws WebloggerException {
        // Efficient aggregate query: count published entries per user, top 3
        // creatorUserName is the persisted String field (creator is a derived method, not a JPA field)
        String jpql =
                "SELECT e.creatorUserName, COUNT(e) " +
                "FROM WeblogEntry e " +
                "WHERE e.status = ?1 " +
                "GROUP BY e.creatorUserName " +
                "ORDER BY COUNT(e) DESC";

        Query q = strategy.getDynamicQuery(jpql);
        q.setParameter(1, PubStatus.PUBLISHED);
        q.setMaxResults(3);

        @SuppressWarnings("unchecked")
        List<Object[]> results = q.getResultList();

        if (results.isEmpty()) {
            return new DashboardMetric(KEY, "Top 3 Most Active Users", "N/A",
                    "No published entries found");
        }

        StringBuilder value = new StringBuilder();
        StringBuilder detail = new StringBuilder();
        int rank = 1;

        for (Object[] row : results) {
            String userName = (String) row[0];
            long entryCount = ((Number) row[1]).longValue();

            if (value.length() > 0) {
                value.append(", ");
            }
            value.append(userName);

            detail.append("#").append(rank).append(" ")
                  .append(userName)
                  .append(" (").append(entryCount).append(" entries)");

            if (rank < results.size()) {
                detail.append(" | ");
            }
            rank++;
        }

        return new DashboardMetric(KEY, "Top 3 Most Active Users",
                value.toString(), detail.toString());
    }
}
