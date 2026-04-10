package org.apache.roller.weblogger.business.dashboard;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DashboardReport implements Serializable {

    private final String viewMode;
    private final List<DashboardMetric> metrics;
    private final long generatedAt;

    DashboardReport(String viewMode, List<DashboardMetric> metrics) {
        this.viewMode = viewMode;
        this.metrics = Collections.unmodifiableList(new ArrayList<>(metrics));
        this.generatedAt = System.currentTimeMillis();
    }

    /** Returns the view mode name, e.g. "minimalist" or "full". */
    public String getViewMode() {
        return viewMode;
    }

    /** Returns the list of metrics included in this report. */
    public List<DashboardMetric> getMetrics() {
        return metrics;
    }

    /** Returns the epoch millis when this report was generated. */
    public long getGeneratedAt() {
        return generatedAt;
    }

    /** Convenience: checks if a metric with the given key exists. */
    public boolean hasMetric(String key) {
        return metrics.stream().anyMatch(m -> m.getKey().equals(key));
    }

    /** Convenience: retrieves a metric by key, or null if not present. */
    public DashboardMetric getMetric(String key) {
        return metrics.stream().filter(m -> m.getKey().equals(key)).findFirst().orElse(null);
    }
}
