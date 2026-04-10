package org.apache.roller.weblogger.business.breakdown;

import java.util.Collections;
import java.util.List;

public class ConversationBreakdown {

    private List<Theme> themes;
    private String overallRecap;
    private String strategyName;

    public ConversationBreakdown() {
        this.themes       = Collections.emptyList();
        this.overallRecap = "";
        this.strategyName = "unknown";
    }

    /**
     * @param themes        discovered themes; must not be {@code null}.
     * @param overallRecap  plain-language summary; must not be {@code null}.
     * @param strategyName  identifier of the strategy that produced this result.
     */
    public ConversationBreakdown(List<Theme> themes,
                                 String overallRecap,
                                 String strategyName) {
        this.themes       = Collections.unmodifiableList(themes);
        this.overallRecap = overallRecap;
        this.strategyName = strategyName;
    }

    // Accessors
    public List<Theme> getThemes() {
        return themes;
    }

    public void setThemes(List<Theme> themes) {
        this.themes = themes;
    }

    public String getOverallRecap() {
        return overallRecap;
    }

    public void setOverallRecap(String overallRecap) {
        this.overallRecap = overallRecap;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }
}
