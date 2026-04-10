package org.apache.roller.weblogger.business.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;

public class DashboardReportBuilder {

    private static final Log log = LogFactory.getLog(DashboardReportBuilder.class);

    private final Map<String, MetricCollector> collectorMap;
    private String viewMode = "full";
    private Set<String> metricKeys;

    /**
     * Creates a new builder with the given set of available collectors.
     * @param collectors all registered metric collectors
     */
    public DashboardReportBuilder(List<MetricCollector> collectors) {
        this.collectorMap = new LinkedHashMap<>();
        for (MetricCollector c : collectors) {
            this.collectorMap.put(c.getMetricKey(), c);
        }
    }

    /**
     * Sets the view mode label (e.g., "minimalist", "full").
     * @param viewMode the name of the view
     * @return this builder for chaining
     */
    public DashboardReportBuilder forView(String viewMode) {
        this.viewMode = viewMode;
        return this;
    }

    /**
     * Specifies which metrics to include in this report by their keys.
     * Only collectors whose keys are in this set will be executed.
     * @param keys the set of metric keys to include
     * @return this builder for chaining
     */
    public DashboardReportBuilder withMetrics(Set<String> keys) {
        this.metricKeys = keys;
        return this;
    }

    /**
     * Builds the DashboardReport by executing only the collectors
     * whose keys match the requested metric set.
     * 
     * If no metric keys are specified, all available collectors are executed.
     * 
     * @return a fully constructed DashboardReport
     * @throws WebloggerException if any collector fails
     */
    public DashboardReport build() throws WebloggerException {
        List<DashboardMetric> metrics = new ArrayList<>();

        for (Map.Entry<String, MetricCollector> entry : collectorMap.entrySet()) {
            String key = entry.getKey();
            MetricCollector collector = entry.getValue();

            // If metric keys are specified, skip collectors not in the set
            if (metricKeys != null && !metricKeys.contains(key)) {
                continue;
            }

            try {
                DashboardMetric metric = collector.collect();
                if (metric != null) {
                    metrics.add(metric);
                }
            } catch (Exception e) {
                log.warn("Failed to collect metric: " + key, e);
                // Add an error metric rather than failing the whole report
                metrics.add(new DashboardMetric(key, key, "Error",
                        "Failed to collect: " + e.getMessage()));
            }
        }

        return new DashboardReport(viewMode, metrics);
    }
}
