<%-- filepath: app/src/main/webapp/WEB-INF/jsps/core/BugReportSubmit.jsp --%>
<%@ include file="/WEB-INF/jsps/taglibs-struts2.jsp" %>

<p class="pagetip">
    Describe the issue clearly so it can be addressed quickly. Fields marked <span style="color:#c00">*</span> are required.
</p>

<s:actionerror />
<s:actionmessage />

<s:form action="bugReport!submit"
        namespace="/roller-ui"
        theme="bootstrap"
        cssClass="form-horizontal">

    <%-- ═══════════════════════════════════════════════════ --%>
    <%-- SECTION 1 — Core details (required)               --%>
    <%-- ═══════════════════════════════════════════════════ --%>
    <div class="panel panel-primary" style="margin-bottom:20px;">
        <div class="panel-heading">
            <h4 class="panel-title" style="margin:0;">
                <span class="glyphicon glyphicon-list-alt" aria-hidden="true"></span>
                &nbsp;Bug Details
            </h4>
        </div>
        <div class="panel-body">

            <%-- Title --%>
            <div class="form-group">
                <label class="col-sm-3 control-label">
                    <s:text name="bugReport.field.title" /> <span style="color:#c00">*</span>
                </label>
                <div class="col-sm-9">
                    <s:textfield name="title"
                                 cssClass="form-control"
                                 maxlength="255"
                                 placeholder="Short, descriptive summary of the issue"
                                 theme="simple"
                                 required="true" />
                </div>
            </div>

            <%-- Category --%>
            <div class="form-group">
                <label class="col-sm-3 control-label">
                    <s:text name="bugReport.field.category" /> <span style="color:#c00">*</span>
                </label>
                <div class="col-sm-9">
                    <s:if test="categoryList != null && !categoryList.isEmpty()">
                        <s:select name="category"
                                  list="categoryList"
                                  headerKey=""
                                  headerValue="-- Select a category --"
                                  cssClass="form-control"
                                  theme="simple"
                                  required="true" />
                    </s:if>
                    <s:else>
                        <s:textfield name="category"
                                     cssClass="form-control"
                                     theme="simple"
                                     required="true" />
                    </s:else>
                </div>
            </div>

            <%-- Severity with colour cues --%>
            <div class="form-group">
                <label class="col-sm-3 control-label">
                    <s:text name="bugReport.field.severity" /> <span style="color:#c00">*</span>
                </label>
                <div class="col-sm-9">
                    <s:if test="severityList != null && !severityList.isEmpty()">
                        <s:select name="severity"
                                  list="severityList"
                                  headerKey=""
                                  headerValue="-- Select severity --"
                                  cssClass="form-control"
                                  theme="simple"
                                  required="true"
                                  id="severitySelect" />
                    </s:if>
                    <s:else>
                        <s:textfield name="severity"
                                     cssClass="form-control"
                                     theme="simple"
                                     required="true" />
                    </s:else>
                    <span class="help-block" id="severityHint" style="font-size:12px;"></span>
                </div>
            </div>

            <%-- Description --%>
            <div class="form-group">
                <label class="col-sm-3 control-label">
                    <s:text name="bugReport.field.description" /> <span style="color:#c00">*</span>
                </label>
                <div class="col-sm-9">
                    <s:textarea name="description"
                                cssClass="form-control"
                                rows="6"
                                theme="simple"
                                placeholder="Describe what happened and what you expected to happen"
                                required="true" />
                </div>
            </div>

        </div><%-- /panel-body --%>
    </div><%-- /panel panel-primary --%>

    <%-- ═══════════════════════════════════════════════════ --%>
    <%-- SECTION 2 — Diagnostic details (optional, collapsible) --%>
    <%-- ═══════════════════════════════════════════════════ --%>
    <div class="panel panel-default" style="margin-bottom:20px;">
        <div class="panel-heading">
            <h4 class="panel-title" style="margin:0;">
                <a data-toggle="collapse" href="#diagCollapse" style="text-decoration:none;color:inherit;">
                    <span class="glyphicon glyphicon-wrench" aria-hidden="true"></span>
                    &nbsp;Diagnostic Information
                    <small style="color:#888; font-size:12px;">&nbsp;(optional — click to expand)</small>
                    <%-- indicator handled by theme/CSS; explicit icon removed to avoid duplicate arrow --%>
                </a>
            </h4>
        </div>
        <div id="diagCollapse" class="collapse">
            <div class="panel-body">

                <%-- Steps to Reproduce --%>
                <div class="form-group">
                    <label class="col-sm-3 control-label">
                        <s:text name="bugReport.field.stepsToReproduce" />
                    </label>
                    <div class="col-sm-9">
                        <s:textarea name="stepsToReproduce"
                                    cssClass="form-control"
                                    rows="4"
                                    theme="simple"
                                    placeholder="1. Go to ...&#10;2. Click on ...&#10;3. See error" />
                    </div>
                </div>

                <%-- Page URL --%>
                <div class="form-group">
                    <label class="col-sm-3 control-label">
                        <s:text name="bugReport.field.pageUrl" />
                    </label>
                    <div class="col-sm-9">
                        <s:textfield name="pageUrl"
                                     cssClass="form-control"
                                     theme="simple"
                                     placeholder="https://..." />
                    </div>
                </div>

                <%-- Browser Info (auto-filled) --%>
                <div class="form-group">
                    <label class="col-sm-3 control-label">
                        <s:text name="bugReport.field.browserInfo" />
                    </label>
                    <div class="col-sm-9">
                        <s:textfield name="browserInfo"
                                     cssClass="form-control"
                                     theme="simple"
                                     id="browserInfoField" />
                        <span class="help-block" style="font-size:11px;">Auto-detected from your browser.</span>
                    </div>
                </div>

                <%-- Screenshot URL --%>
                <div class="form-group">
                    <label class="col-sm-3 control-label">
                        <s:text name="bugReport.field.screenshotUrl" />
                    </label>
                    <div class="col-sm-9">
                        <s:textfield name="screenshotUrl"
                                     cssClass="form-control"
                                     theme="simple"
                                     placeholder="Link to screenshot or screen recording" />
                    </div>
                </div>

            </div><%-- /panel-body --%>
        </div><%-- /collapse --%>
    </div><%-- /panel --%>

    <%-- ═══════════════════════════════════════════════════ --%>
    <%-- Action buttons                                    --%>
    <%-- ═══════════════════════════════════════════════════ --%>
    <div class="form-group">
        <div class="col-sm-offset-3 col-sm-9">
            <s:submit value="%{getText('generic.submit')}"
                      cssClass="btn btn-primary"
                      theme="simple" />
            &nbsp;
            <a href="<s:url namespace='/roller-ui' action='bugReportList'/>" class="btn btn-default">
                <s:text name="generic.cancel" />
            </a>
        </div>
    </div>

</s:form>

<%-- Auto-fill browser info + severity hint --%>
<script type="text/javascript">
(function() {
    // Browser info
    try {
        var bf = document.getElementById('browserInfoField');
        function detectBrowserInfo() {
            try {
                // Prefer Client Hints when available (modern Chrome)
                if (navigator.userAgentData && navigator.userAgentData.brands) {
                    var brands = navigator.userAgentData.brands.map(function(b){ return b.brand + ' ' + b.version; }).join(', ');
                    var plat = navigator.userAgentData.platform || navigator.platform || '';
                    return brands + (plat ? ' on ' + plat : '');
                }
            } catch(e) {}

            var ua = navigator.userAgent || '';
            var platform = navigator.platform || '';
            var m;
            if ((m = ua.match(/(Chrome|CriOS)\/(\d+[\.\d]*)/))) {
                return m[1] + ' ' + m[2] + (platform ? ' on ' + platform : '');
            }
            if ((m = ua.match(/(Firefox)\/(\d+[\.\d]*)/))) {
                return m[1] + ' ' + m[2] + (platform ? ' on ' + platform : '');
            }
            if ((m = ua.match(/Version\/(\d+[\.\d]*).*Safari/))) {
                return 'Safari ' + m[1] + (platform ? ' on ' + platform : '');
            }
            // Fallback to raw UA string
            return ua;
        }
        if (bf && !bf.value) { bf.value = detectBrowserInfo(); }
    } catch(e) {}

    // Severity colour hint
    var hints = {
        'CRITICAL': { cls: 'danger',  msg: 'System is unusable or data loss may occur.' },
        'HIGH':     { cls: 'warning', msg: 'Major feature broken, no workaround.' },
        'MEDIUM':   { cls: 'info',    msg: 'Feature partially broken — a workaround exists.' },
        'LOW':      { cls: 'default', msg: 'Minor issue, cosmetic or trivial impact.' }
    };
    var sel = document.getElementById('severitySelect');
    var hint = document.getElementById('severityHint');
    function updateHint() {
        if (!sel || !hint) return;
        var v = sel.value, h = hints[v];
        hint.innerHTML = h ? '<span class="label label-' + h.cls + '">' + v + '</span> ' + h.msg : '';
    }
    if (sel) { sel.addEventListener('change', updateHint); updateHint(); }
})();
</script>