package org.apache.roller.weblogger.business.notification.template;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.roller.weblogger.pojos.BugReport;

/**
 * Fluent Builder pattern — builds HTML email body section by section.
 */
public class EmailBodyBuilder {

    private static final SimpleDateFormat DATE_FMT =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final StringBuilder html = new StringBuilder();
    private boolean headerClosed = false;

    public EmailBodyBuilder() {
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/>")
            .append("<style>")
            .append("body{margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;font-size:14px;color:#333;}")
            .append(".wrap{max-width:620px;margin:30px auto;background:#fff;border-radius:6px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);}")
            .append(".body{padding:24px;}")
            .append(".badge{display:inline-block;padding:3px 10px;border-radius:12px;font-size:12px;font-weight:bold;color:#fff;}")
            .append(".sev-critical,.sev-CRITICAL{background:#d9534f;}")
            .append(".sev-high,.sev-HIGH{background:#e8622a;}")
            .append(".sev-medium,.sev-MEDIUM{background:#f0ad4e;color:#333;}")
            .append(".sev-low,.sev-LOW{background:#5cb85c;}")
            .append("table.info{width:100%;border-collapse:collapse;margin:16px 0;}")
            .append("table.info td{padding:9px 10px;border-bottom:1px solid #eee;vertical-align:top;}")
            .append("table.info td.lbl{width:140px;font-weight:bold;color:#555;white-space:nowrap;}")
            .append(".section{margin:18px 0;padding:14px;background:#fafafa;border-radius:3px;}")
            .append(".section h4{margin:0 0 8px;font-size:13px;text-transform:uppercase;letter-spacing:.5px;}")
            .append(".section p{margin:0;white-space:pre-wrap;line-height:1.6;}")
            .append(".status-flow{text-align:center;padding:20px;background:#f0f9ff;border-radius:6px;margin:16px 0;}")
            .append(".s-old{display:inline-block;padding:6px 16px;background:#e0e0e0;color:#666;border-radius:20px;font-weight:bold;font-size:13px;}")
            .append(".s-new{display:inline-block;padding:6px 16px;background:#5cb85c;color:#fff;border-radius:20px;font-weight:bold;font-size:13px;}")
            .append(".arrow{font-size:22px;margin:0 14px;vertical-align:middle;}")
            .append(".notice{border-radius:6px;padding:14px 16px;margin:18px 0;font-size:14px;}")
            .append(".ftr{background:#f5f5f5;padding:12px 24px;font-size:11px;color:#999;border-top:1px solid #e5e5e5;text-align:center;}")
            .append("</style></head><body><div class='wrap'>");
    }

    public EmailBodyBuilder header(String color, String title, String subtitle) {
        html.append("<div style='background:").append(color)
            .append(";color:white;padding:20px 24px;'>")
            .append("<h2 style='margin:0;font-size:20px;'>").append(title).append("</h2>");
        if (subtitle != null && !subtitle.isEmpty()) {
            html.append("<p style='margin:4px 0 0;font-size:13px;opacity:.85;'>")
                .append(subtitle).append("</p>");
        }
        html.append("</div><div class='body'>");
        headerClosed = true;
        return this;
    }

    public EmailBodyBuilder intro(String text) {
        html.append("<p>").append(text).append("</p>");
        return this;
    }

    public EmailBodyBuilder outro(String text) {
        if (notBlank(text))
            html.append("<p style='margin-top:15px;'>").append(text).append("</p>");
        return this;
    }

    public EmailBodyBuilder reportTable(BugReport r) {
        html.append("<table class='info'>");
        row("Title",    "<strong>" + safe(r.getTitle()) + "</strong>");
        if (r.getCreator() != null) {
            row("Reporter", safe(r.getCreator().getScreenName())
                + " &lt;" + safe(r.getCreator().getEmailAddress()) + "&gt;");
        }
        row("Category", safe(r.getCategory()));
        row("Severity",
            "<span class='badge sev-" + safe(r.getSeverity()) + "'>"
            + safe(r.getSeverity()) + "</span>");
        row("Status",   "<strong>" + safe(r.getStatus()) + "</strong>");
        row("Submitted", formatDate(r.getCreatedAt()));
        if (notBlank(r.getPageUrl()))
            row("Page URL",
                "<a href='" + safe(r.getPageUrl()) + "'>" + safe(r.getPageUrl()) + "</a>");
        if (notBlank(r.getBrowserInfo()))
            row("Browser", safe(r.getBrowserInfo()));
        html.append("</table>");
        return this;
    }

    public EmailBodyBuilder statusFlow(String oldStatus, String newStatus) {
        html.append("<div class='status-flow'>")
            .append("<span class='s-old'>").append(safe(oldStatus)).append("</span>")
            .append("<span class='arrow'>&rarr;</span>")
            .append("<span class='s-new'>").append(safe(newStatus)).append("</span>")
            .append("</div>");
        return this;
    }

    public EmailBodyBuilder section(String title, String body, String borderColor) {
        if (!notBlank(body)) return this;
        html.append("<div class='section' style='border-left:4px solid ")
            .append(borderColor).append(";'>")
            .append("<h4 style='color:").append(borderColor).append(";'>")
            .append(safe(title)).append("</h4>")
            .append("<p>").append(safe(body)).append("</p>")
            .append("</div>");
        return this;
    }

    public EmailBodyBuilder noticeBox(String text, String bgColor, String borderColor, String textColor) {
        html.append("<div class='notice' style='background:").append(bgColor)
            .append(";border:1px solid ").append(borderColor)
            .append(";color:").append(textColor).append(";'>")
            .append(text)
            .append("</div>");
        return this;
    }

    public EmailBodyBuilder actorBox(String label, String actor, String borderColor) {
        html.append("<div style='background:#f9f9f9;border-left:4px solid ")
            .append(borderColor)
            .append(";padding:10px 14px;border-radius:3px;margin-bottom:16px;'>")
            .append("").append(label).append(": <strong>")
            .append(safe(actor)).append("</strong></div>");
        return this;
    }

    public EmailBodyBuilder footer(String contextNote) {
        html.append("</div>"); // close .body
        html.append("<div class='ftr'>")
            .append("Apache Roller Bug Tracker &mdash; ")
            .append(safe(contextNote))
            .append("<br/>This is an automated notification. Please do not reply.")
            .append("</div>");
        return this;
    }

    public String build() {
        html.append("</div></body></html>"); // close .wrap
        return html.toString();
    }

    // --- helpers ---
    private void row(String label, String value) {
        html.append("<tr><td class='lbl'>").append(label).append(":</td>")
            .append("<td>").append(value).append("</td></tr>");
    }

    private static String safe(String s)      { return s != null ? s : ""; }
    private static boolean notBlank(String s)  { return s != null && !s.trim().isEmpty(); }
    private static String formatDate(Date d)   {
        return d != null ? DATE_FMT.format(d) : "N/A";
    }
}