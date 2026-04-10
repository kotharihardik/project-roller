package org.apache.roller.weblogger.business.discussion;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DiscussionOverview {

    // Indicator 1 – Activity Level
    private int totalComments;
    private double avgCommentsPerDay;
    private String activityLabel;

    // Indicator 2 – Comment-Type Distribution
    private Map<String, Integer> typeDistribution;
    private String dominantType;

    // Indicator 3 – Top Recurring Keywords
    private List<Map.Entry<String, Integer>> topKeywords;

    public DiscussionOverview() {
        typeDistribution  = Collections.emptyMap();
        topKeywords       = Collections.emptyList();
        activityLabel     = "No Comments";
        dominantType      = "Neutral";
    }

    public int getTotalComments() {
        return totalComments;
    }

    public void setTotalComments(int totalComments) {
        this.totalComments = totalComments;
    }

    public double getAvgCommentsPerDay() {
        return avgCommentsPerDay;
    }

    public void setAvgCommentsPerDay(double avgCommentsPerDay) {
        this.avgCommentsPerDay = avgCommentsPerDay;
    }

    public String getActivityLabel() {
        return activityLabel;
    }

    public void setActivityLabel(String activityLabel) {
        this.activityLabel = activityLabel;
    }

    public Map<String, Integer> getTypeDistribution() {
        return typeDistribution;
    }

    public void setTypeDistribution(Map<String, Integer> typeDistribution) {
        this.typeDistribution = typeDistribution;
    }

    public String getDominantType() {
        return dominantType;
    }

    public void setDominantType(String dominantType) {
        this.dominantType = dominantType;
    }

    public List<Map.Entry<String, Integer>> getTopKeywords() {
        return topKeywords;
    }

    public void setTopKeywords(List<Map.Entry<String, Integer>> topKeywords) {
        this.topKeywords = topKeywords;
    }
}
