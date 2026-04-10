package org.apache.roller.weblogger.business.dashboard;

import java.io.Serializable;

public class DashboardMetric implements Serializable {

    private final String key;
    private final String label;
    private final String value;
    private final String detail;

    public DashboardMetric(String key, String label, String value) {
        this(key, label, value, null);
    }

    public DashboardMetric(String key, String label, String value, String detail) {
        this.key = key;
        this.label = label;
        this.value = value;
        this.detail = detail;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "DashboardMetric{key='" + key + "', label='" + label + "', value='" + value + "'}";
    }
}
