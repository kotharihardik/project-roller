<%@ include file="/WEB-INF/jsps/taglibs-struts2.jsp" %>

<style>
    .dashboard-container {
        max-width: 1200px;
        margin: 0 auto;
    }
    .dashboard-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 20px;
        padding-bottom: 10px;
        border-bottom: 2px solid #ddd;
    }
    .dashboard-header h2 {
        margin: 0;
        color: #333;
    }
    .view-toggle {
        display: flex;
        align-items: center;
        gap: 10px;
    }
    .view-toggle label {
        font-weight: bold;
        margin-right: 5px;
    }
    .view-badge {
        display: inline-block;
        padding: 4px 12px;
        border-radius: 12px;
        font-size: 12px;
        font-weight: bold;
        text-transform: uppercase;
        letter-spacing: 0.5px;
    }
    .view-badge.minimalist {
        background-color: #d4edda;
        color: #155724;
    }
    .view-badge.full {
        background-color: #cce5ff;
        color: #004085;
    }
    .metrics-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
        gap: 20px;
        margin-top: 20px;
    }
    .metric-card {
        background: #fff;
        border: 1px solid #e0e0e0;
        border-radius: 8px;
        padding: 20px;
        box-shadow: 0 2px 4px rgba(0,0,0,0.05);
        transition: box-shadow 0.2s ease;
    }
    .metric-card:hover {
        box-shadow: 0 4px 8px rgba(0,0,0,0.1);
    }
    .metric-card .metric-label {
        font-size: 13px;
        color: #666;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        margin-bottom: 8px;
    }
    .metric-card .metric-value {
        font-size: 24px;
        font-weight: bold;
        color: #2c3e50;
        margin-bottom: 6px;
        word-wrap: break-word;
    }
    .metric-card .metric-detail {
        font-size: 12px;
        color: #999;
    }
    .dashboard-footer {
        margin-top: 30px;
        padding-top: 10px;
        border-top: 1px solid #eee;
        font-size: 12px;
        color: #aaa;
    }
    .no-metrics {
        text-align: center;
        padding: 40px;
        color: #999;
        font-style: italic;
    }
</style>

<div class="dashboard-container">

    <%-- Dashboard Header with View Toggle --%>
    <div class="dashboard-header">
        <div>
            <p class="text-muted"><s:text name="siteSummaryDashboard.subtitle" /></p>
        </div>
        <div class="view-toggle">
            <label><s:text name="siteSummaryDashboard.viewMode" />:</label>
            <s:form action="siteSummaryDashboard!toggleView" method="post" theme="simple">
                <s:hidden name="salt" />
                <s:hidden name="viewMode" value="%{viewMode}" />
                <s:if test="viewMode == 'minimalist'">
                    <button type="submit" class="btn btn-primary btn-sm" title="Switch to Full View">
                        <s:text name="siteSummaryDashboard.switchToFull" />
                    </button>
                </s:if>
                <s:else>
                    <button type="submit" class="btn btn-success btn-sm" title="Switch to Minimalist View">
                        <s:text name="siteSummaryDashboard.switchToMinimalist" />
                    </button>
                </s:else>
            </s:form>
            <span class="view-badge <s:property value='viewMode' />">
                <s:property value="viewMode" />
            </span>
        </div>
    </div>

    <%-- Metrics Grid --%>
    <s:if test="report != null && !report.metrics.isEmpty">
        <div class="metrics-grid">
            <s:iterator var="metric" value="report.metrics">
                <div class="metric-card">
                    <div class="metric-label"><s:property value="#metric.label" /></div>
                    <div class="metric-value"><s:property value="#metric.value" /></div>
                    <s:if test="#metric.detail != null && #metric.detail.length() > 0">
                        <div class="metric-detail"><s:property value="#metric.detail" /></div>
                    </s:if>
                </div>
            </s:iterator>
        </div>
    </s:if>
    <s:else>
        <div class="no-metrics">
            <p><s:text name="siteSummaryDashboard.noMetrics" /></p>
        </div>
    </s:else>

    <%-- Footer --%>
    <div class="dashboard-footer">
        <s:text name="siteSummaryDashboard.generatedAt" />:
        <s:if test="report != null">
            <s:date name="report.generatedAt" format="yyyy-MM-dd HH:mm:ss" />
        </s:if>
        <s:else>
            N/A
        </s:else>
        &nbsp;|&nbsp;
        <s:text name="siteSummaryDashboard.viewMode" />:
        <strong><s:property value="viewMode" /></strong>
        &nbsp;|&nbsp;
        <s:text name="siteSummaryDashboard.metricCount" />:
        <strong><s:if test="report != null"><s:property value="report.metrics.size" /></s:if><s:else>0</s:else></strong>
    </div>
</div>
