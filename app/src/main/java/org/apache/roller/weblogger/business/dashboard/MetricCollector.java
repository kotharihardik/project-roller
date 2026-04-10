package org.apache.roller.weblogger.business.dashboard;

import org.apache.roller.weblogger.WebloggerException;

public interface MetricCollector {

    /**
     * Returns a unique key identifying this metric.
     * Used by the Builder to decide which metrics to include in a view.
     */
    String getMetricKey();

    /**
     * Collects and returns the metric value.
     * 
     * @return a DashboardMetric containing the computed data
     * @throws WebloggerException if data retrieval fails
     */
    DashboardMetric collect() throws WebloggerException;
}
