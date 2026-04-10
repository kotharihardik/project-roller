<%-- BugReportAdmin.jsp — Admin Bug Report Dashboard --%>
<%@ include file="/WEB-INF/jsps/taglibs-struts2.jsp" %>

<style type="text/css">
/* ═══════════ Stat cards ═══════════ */
.br-stat-card                { border-radius:8px; box-shadow:0 2px 10px rgba(0,0,0,.1); border:none; }
.br-stat-card .panel-heading { font-size:.78em; letter-spacing:.7px; text-transform:uppercase;
                                font-weight:700; border-radius:8px 8px 0 0; }
.br-stat-card .panel-body    { font-size:2.6em; font-weight:700; text-align:center; padding:12px 0 10px; }

/* ═══════════ Filter bar ═══════════ */
.br-filter-bar { background:#f6f8fb; padding:12px 16px; border-radius:6px;
                 margin-bottom:22px; border:1px solid #dde2ea; }
.br-filter-bar label { font-size:.83em; color:#555; margin:0 6px 0 0; font-weight:600; }

/* ═══════════ Report card ═══════════ */
.report-card {
    background:#fff; border-radius:8px;
    box-shadow:0 1px 5px rgba(0,0,0,.08); margin-bottom:14px;
    border-left:5px solid #ccc; overflow:hidden;
    transition:box-shadow .2s, transform .15s;
}
.report-card:hover { box-shadow:0 5px 20px rgba(0,0,0,.13); transform:translateY(-1px); }
.report-card[data-sev="CRITICAL"] { border-left-color:#d9534f; }
.report-card[data-sev="HIGH"]     { border-left-color:#e67e22; }
.report-card[data-sev="MEDIUM"]   { border-left-color:#f0ad4e; }
.report-card[data-sev="LOW"]      { border-left-color:#5bc0de; }
.report-card[data-sev="INFO"]     { border-left-color:#95a5a6; }

.rc-hdr  { padding:10px 16px 6px; display:flex; align-items:center; flex-wrap:wrap; gap:6px; }
.rc-body { padding:2px 16px 10px; }
.rc-ftr  { padding:8px 16px 10px; border-top:1px solid #f0f2f5;
            background:#fafbfc; display:flex; flex-wrap:wrap; align-items:flex-start; gap:8px; }

.rc-title   { font-size:1.05em; font-weight:600; color:#1a2a3a; margin:0 0 3px; }
.rc-meta    { font-size:.8em; color:#8a9bae; margin:0; }
.rc-meta span+span::before { content:" · "; }
.rc-preview { font-size:.86em; color:#5a6a7a; margin-top:5px;
              display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }

/* severity badges */
.sev-badge { display:inline-block; padding:2px 10px; border-radius:20px;
             font-size:.71em; font-weight:700; text-transform:uppercase; letter-spacing:.5px; }
.sev-badge[data-sev="CRITICAL"] { background:#fdecea; color:#c0392b; }
.sev-badge[data-sev="HIGH"]     { background:#fdeee0; color:#d35400; }
.sev-badge[data-sev="MEDIUM"]   { background:#fefce6; color:#9a7d0a; }
.sev-badge[data-sev="LOW"]      { background:#e8f6fc; color:#1a7ab0; }
.sev-badge[data-sev="INFO"]     { background:#f2f4f6; color:#6c757d; }

/* status badges */
.stat-badge { display:inline-block; padding:2px 10px; border-radius:20px;
              font-size:.71em; font-weight:700; text-transform:uppercase; letter-spacing:.5px; }
.stat-badge[data-status="OPEN"]        { background:#fdecea; color:#c0392b; }
.stat-badge[data-status="IN_PROGRESS"] { background:#fdeee0; color:#d35400; }
.stat-badge[data-status="RESOLVED"]    { background:#e8f8f0; color:#1a6e38; }
.stat-badge[data-status="CLOSED"]      { background:#f2f4f6; color:#6c757d; }
.stat-badge[data-status="DELETED"]     { background:#f2f4f6; color:#b0b8c0; text-decoration:line-through; }

/* ═══════════ Detail modal ═══════════ */
#bugDetailModal .modal-dialog  { max-width:720px; width:92%; margin:40px auto; }
#bugDetailModal .modal-content { border:none; border-radius:10px;
                                  box-shadow:0 10px 50px rgba(0,0,0,.28); }
#bugDetailModal .modal-header  {
    background:linear-gradient(135deg,#2c3e50 0%,#3d556e 100%);
    color:#fff; border-radius:10px 10px 0 0; padding:18px 22px;
}
#bugDetailModal .modal-header .modal-title { font-weight:700; font-size:1.06em; letter-spacing:.2px; }
#bugDetailModal .modal-header .close       { color:#fff; opacity:.8; font-size:1.5em; margin-top:-2px; }
#bugDetailModal .modal-header .close:hover { opacity:1; }
#bugDetailModal .modal-body   { max-height:68vh; overflow-y:auto; padding:22px 26px; }
#bugDetailModal .modal-footer { background:#f6f8fb; border-radius:0 0 10px 10px; border-top:1px solid #e8ecf1; }
#bugDetailModal .modal-body::-webkit-scrollbar       { width:6px; }
#bugDetailModal .modal-body::-webkit-scrollbar-thumb { background:#c9d3dc; border-radius:3px; }

.brd-sec          { margin-bottom:22px; }
.brd-sec-title    { font-size:.69em; text-transform:uppercase; letter-spacing:.9px;
                    color:#93a8be; font-weight:700; margin-bottom:10px;
                    border-bottom:2px solid #eef1f5; padding-bottom:5px; }
.brd-meta-grid    { display:grid; grid-template-columns:1fr 1fr; gap:12px 30px; }
.brd-meta-item label     { display:block; font-size:.7em; text-transform:uppercase;
                           letter-spacing:.6px; color:#93a8be; margin-bottom:3px; }
.brd-meta-item .brd-val  { font-size:.92em; font-weight:600; color:#2c3e50; }
.brd-text-box     { font-size:.89em; color:#2c3e50; white-space:pre-wrap; word-break:break-word;
                    line-height:1.65; background:#f6f8fb; padding:11px 14px;
                    border-radius:6px; border-left:3px solid #d8e0ea; }
.brd-text-box.empty { color:#b0bec5; font-style:italic; background:transparent; border-left-color:transparent; }
.brd-url-link     { font-size:.88em; word-break:break-all; }
.brd-notes-box    { background:#fffde7; border-left:4px solid #f4c60a;
                    padding:11px 15px; border-radius:0 6px 6px 0;
                    font-size:.9em; white-space:pre-wrap; word-break:break-word; line-height:1.6; }
.brd-screenshot-wrap     { border:1px solid #dde2ea; border-radius:8px; overflow:hidden; margin-top:6px; }
.brd-screenshot-wrap img { max-width:100%; display:block; }
</style>

<%-- ════════════════════════════════
     SUMMARY STAT CARDS
     ════════════════════════════════ --%>
<div class="row" style="margin-bottom:24px;">

    <div class="col-sm-6 col-md-3">
        <div class="panel panel-danger br-stat-card">
            <div class="panel-heading">
                <span class="glyphicon glyphicon-exclamation-sign"></span>
                &nbsp;<s:text name="bugReport.dashboard.open"/>
            </div>
            <div class="panel-body"><s:property value="openCount"/></div>
        </div>
    </div>

    <div class="col-sm-6 col-md-3">
        <div class="panel panel-warning br-stat-card">
            <div class="panel-heading">
                <span class="glyphicon glyphicon-wrench"></span>
                &nbsp;<s:text name="bugReport.dashboard.inProgress"/>
            </div>
            <div class="panel-body"><s:property value="inProgressCount"/></div>
        </div>
    </div>

    <div class="col-sm-6 col-md-3">
        <div class="panel panel-success br-stat-card">
            <div class="panel-heading">
                <span class="glyphicon glyphicon-ok-circle"></span>
                &nbsp;<s:text name="bugReport.dashboard.resolved"/>
            </div>
            <div class="panel-body"><s:property value="resolvedCount"/></div>
        </div>
    </div>

    <div class="col-sm-6 col-md-3">
        <div class="panel panel-default br-stat-card">
            <div class="panel-heading">
                <span class="glyphicon glyphicon-minus-sign"></span>
                &nbsp;<s:text name="bugReport.dashboard.closed"/>
            </div>
            <div class="panel-body"><s:property value="closedCount"/></div>
        </div>
    </div>

</div>

<%-- ════════════════════════════════
     FILTER BAR
     ════════════════════════════════ --%>
<div class="br-filter-bar">
    <s:form action="bugReportAdmin" theme="simple" cssClass="form-inline" style="margin:0;">
        <label><s:text name="bugReport.admin.filterByStatus"/></label>
        <s:select name="statusFilter"
                  list="statusList"
                  cssClass="form-control input-sm"
                  style="margin-right:16px;"/>

        <label><s:text name="bugReport.admin.filterBySeverity"/></label>
        <s:select name="severityFilter"
                  list="severityList"
                  cssClass="form-control input-sm"
                  style="margin-right:12px;"/>

        <s:submit value="%{getText('generic.filter')}"
                  cssClass="btn btn-default btn-sm"/>
    </s:form>
</div>

<%-- ════════════════════════════════
     REPORT CARDS
     ════════════════════════════════ --%>
<s:if test="reports == null || reports.size == 0">
    <div class="alert alert-info" style="border-radius:8px;">
        <span class="glyphicon glyphicon-info-sign"></span>
        &nbsp;<s:text name="bugReport.admin.empty"/>
    </div>
</s:if>

<s:else>

<s:iterator value="reports" var="report">

<s:set var="rid"   value="#report.id"/>
<s:set var="rsev"  value="#report.severity"/>
<s:set var="rstat" value="#report.status"/>

<div class="report-card"
     data-sev="<s:property value='#rsev'/>"
     data-status="<s:property value='#rstat'/>">

    <%-- Header --%>
    <div class="rc-hdr">

        <span class="sev-badge" data-sev="<s:property value='#rsev'/>">
            <s:property value="#rsev"/>
        </span>

        <span class="stat-badge" data-status="<s:property value='#rstat'/>">
            <s:property value="#rstat"/>
        </span>

        <button type="button"
                class="btn btn-info btn-xs btn-report-details"
                data-rid="<s:property value='#rid'/>"
                style="margin-left:auto; padding:3px 10px;">
            <span class="glyphicon glyphicon-search"></span>
            &nbsp;<s:text name="bugReport.admin.viewDetails"/>
        </button>

    </div>

    <%-- Body --%>
    <div class="rc-body">

        <div class="rc-title">
            <s:property value="#report.title" escapeHtml="true"/>
        </div>

        <p class="rc-meta">
            <span>
                <span class="glyphicon glyphicon-user" style="font-size:.8em;"></span>
                &nbsp;<s:if test="#report.creator != null"><s:property value="#report.creator.screenName" escapeHtml="true"/></s:if><s:else>Anonymous</s:else>
            </span>
            <span>
                <span class="glyphicon glyphicon-folder-open" style="font-size:.8em;"></span>
                &nbsp;<s:property value="#report.category" escapeHtml="true"/>
            </span>
            <span>
                <span class="glyphicon glyphicon-calendar" style="font-size:.8em;"></span>
                &nbsp;<s:date name="#report.createdAt" format="MMM d, yyyy"/>
            </span>
            <s:if test="#report.adminNotes != null && #report.adminNotes != ''">
                <span>
                    <span class="label label-warning" style="font-size:.72em;">
                        <span class="glyphicon glyphicon-comment"></span> Has admin note
                    </span>
                </span>
            </s:if>
        </p>

        <s:if test="#report.description != null && #report.description != ''">
            <div class="rc-preview">
                <s:property value="#report.description" escapeHtml="true"/>
            </div>
        </s:if>

    </div>

    <%-- Footer: status update + notes collapse + delete --%>
    <div class="rc-ftr">

        <s:form action="bugReportAdmin!update" theme="simple" style="margin:0; flex:1; min-width:0;">

            <s:hidden name="reportId"       value="%{#report.id}"/>
            <s:hidden name="statusFilter"   value="%{statusFilter}"/>
            <s:hidden name="severityFilter" value="%{severityFilter}"/>
            <s:hidden name="page"           value="%{page}"/>

            <div style="display:flex; flex-wrap:wrap; align-items:center; gap:5px;">

                <s:select name="newStatus"
                          list="statusList"
                          cssClass="form-control input-sm"
                          style="width:136px;"/>

                <s:submit value="%{getText('bugReport.admin.update')}"
                          cssClass="btn btn-primary btn-sm"/>

                <button type="button"
                        class="btn btn-default btn-sm"
                        data-toggle="collapse"
                        data-target="#anotes-<s:property value='#rid'/>">
                    <span class="glyphicon glyphicon-pencil"></span>
                    &nbsp;<s:text name="bugReport.admin.addNote"/>
                </button>

            </div>

            <div id="anotes-<s:property value='#rid'/>" class="collapse" style="margin-top:8px;">
                <s:textarea name="adminNotes"
                            cssClass="form-control"
                            rows="2"
                            placeholder="Private admin note — saved in the database, visible in detail view..."
                            style="font-size:.84em; resize:vertical;"/>
            </div>

        </s:form>

        <s:if test="#rstat != 'DELETED'">
            <s:form action="bugReportAdmin!delete" theme="simple" style="margin:0; align-self:flex-start;">
                <s:hidden name="reportId"       value="%{#report.id}"/>
                <s:hidden name="statusFilter"   value="%{statusFilter}"/>
                <s:hidden name="severityFilter" value="%{severityFilter}"/>
                <s:hidden name="page"           value="%{page}"/>
                <s:submit value="%{getText('generic.delete')}"
                          cssClass="btn btn-danger btn-sm"
                          onclick="return confirm('Delete this bug report?');"/>
            </s:form>
        </s:if>

    </div>

    <%-- Full-detail data block (inside card div = valid HTML; read by JS) --%>
    <div id="rd-<s:property value='#rid'/>" style="display:none;" aria-hidden="true">
        <span data-f="title"><s:property        value="#report.title"              escapeHtml="true"/></span>
        <span data-f="reporter"><s:if test="#report.creator != null"><s:property value="#report.creator.screenName" escapeHtml="true"/></s:if></span>
        <span data-f="category"><s:property     value="#report.category"           escapeHtml="true"/></span>
        <span data-f="severity"><s:property     value="#report.severity"           escapeHtml="true"/></span>
        <span data-f="status"><s:property       value="#report.status"             escapeHtml="true"/></span>
        <span data-f="date"><s:date name="#report.createdAt" format="MMM d, yyyy 'at' HH:mm"/></span>
        <span data-f="description"><s:property  value="#report.description"        escapeHtml="true"/></span>
        <span data-f="steps"><s:property        value="#report.stepsToReproduce"   escapeHtml="true"/></span>
        <span data-f="pageUrl"><s:property      value="#report.pageUrl"            escapeHtml="true"/></span>
        <span data-f="browserInfo"><s:property  value="#report.browserInfo"        escapeHtml="true"/></span>
        <span data-f="screenshotUrl"><s:property value="#report.screenshotUrl"     escapeHtml="true"/></span>
        <span data-f="adminNotes"><s:property   value="#report.adminNotes"         escapeHtml="true"/></span>
    </div>

</div>

</s:iterator>

<div style="margin-top:12px;">
    <s:if test="page > 0">
        <a href="<s:url action='bugReportAdmin'><s:param name='page' value='%{page-1}'/><s:param name='statusFilter' value='%{statusFilter}'/><s:param name='severityFilter' value='%{severityFilter}'/></s:url>"
           class="btn btn-default btn-sm">
            &laquo; <s:text name="generic.previous"/>
        </a>
    </s:if>
    <s:if test="hasMore">
        <a href="<s:url action='bugReportAdmin'><s:param name='page' value='%{page+1}'/><s:param name='statusFilter' value='%{statusFilter}'/><s:param name='severityFilter' value='%{severityFilter}'/></s:url>"
           class="btn btn-default btn-sm">
            <s:text name="generic.next"/> &raquo;
        </a>
    </s:if>
</div>

</s:else>

<%-- ════════════════
     DETAIL MODAL
     ════════════════ --%>
<div id="bugDetailModal" class="modal fade" tabindex="-1" role="dialog"
     aria-labelledby="bugDetailModalLabel">
    <div class="modal-dialog" role="document">
        <div class="modal-content">

            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                    <span aria-hidden="true">&times;</span>
                </button>
                <h4 class="modal-title" id="bugDetailModalLabel">
                    <span class="glyphicon glyphicon-bug"></span>&nbsp;Bug Report Detail
                </h4>
            </div>

            <div class="modal-body" id="bugDetailModalBody">
            </div>

            <div class="modal-footer">
                <button type="button" class="btn btn-default" data-dismiss="modal">
                    <span class="glyphicon glyphicon-remove"></span>&nbsp;<s:text name="generic.close"/>
                </button>
            </div>

        </div>
    </div>
</div>

<script type="text/javascript">
/*<![CDATA[*/
(function ($) {

    var SEV_LABEL  = { CRITICAL:'danger', HIGH:'warning', MEDIUM:'warning', LOW:'info', INFO:'default' };
    var STAT_LABEL = { OPEN:'danger', IN_PROGRESS:'warning', RESOLVED:'success',
                       CLOSED:'default', DELETED:'default' };

    function f(ctx, name) {
        return ($('[data-f="' + name + '"]', ctx).text() || '').trim();
    }

    function safeUrl(raw) {
        if (!raw) return null;
        if (/^(javascript|data|vbscript):/i.test(raw.replace(/\s/g, ''))) return null;
        return raw;
    }

    function $section(title) {
        return $('<div class="brd-sec">').append($('<div class="brd-sec-title">').text(title));
    }

    function $textBlock(val, emptyMsg) {
        if (val) return $('<div class="brd-text-box">').text(val);
        return $('<div class="brd-text-box empty">').text(emptyMsg || 'Not provided.');
    }

    function buildModal(rid) {
        var $c = $('#rd-' + rid);
        if (!$c.length) {
            $('#bugDetailModalBody').empty().append(
                $('<div class="alert alert-warning">').text('Detail data not found for this report.')
            );
            return;
        }

        var title         = f($c, 'title');
        var reporter      = f($c, 'reporter');
        var category      = f($c, 'category');
        var severity      = f($c, 'severity');
        var status        = f($c, 'status');
        var date          = f($c, 'date');
        var description   = f($c, 'description');
        var steps         = f($c, 'steps');
        var pageUrl       = safeUrl(f($c, 'pageUrl'));
        var browserInfo   = f($c, 'browserInfo');
        var screenshotUrl = safeUrl(f($c, 'screenshotUrl'));
        var adminNotes    = f($c, 'adminNotes');

        $('#bugDetailModalLabel')
            .text('\u00a0' + (title || 'Bug Report'))
            .prepend($('<span class="glyphicon glyphicon-bug">'));

        var $body = $('#bugDetailModalBody').empty();

        /* 1. Meta */
        var $s1   = $section('Report Information');
        var $grid = $('<div class="brd-meta-grid">');

        function metaItem(label, val) {
            return $('<div class="brd-meta-item">')
                .append($('<label>').text(label))
                .append($('<div class="brd-val">').text(val || '\u2014'));
        }
        function badgeItem(label, val, cls) {
            return $('<div class="brd-meta-item">')
                .append($('<label>').text(label))
                .append($('<div>').append(
                    $('<span class="label label-' + cls + '" style="font-size:.84em;">').text(val || '\u2014')
                ));
        }

        $grid.append(metaItem('Reported By', reporter || 'Anonymous'));
        $grid.append(metaItem('Category',    category));
        $grid.append(badgeItem('Severity',   severity, SEV_LABEL[severity]  || 'default'));
        $grid.append(badgeItem('Status',     status,   STAT_LABEL[status]   || 'default'));
        $grid.append(metaItem('Reported On', date));
        $s1.append($grid);
        $body.append($s1);

        /* 2. Description */
        $body.append($section('Description').append($textBlock(description, 'No description was provided.')));

        /* 3. Steps */
        $body.append($section('Steps to Reproduce').append($textBlock(steps, 'No steps were provided.')));

        /* 4. Technical */
        var $s4 = $section('Technical Information');
        var $tg = $('<div class="brd-meta-grid">');

        var $urlRow = $('<div class="brd-meta-item" style="grid-column:1/-1;">')
            .append($('<label>').text('Page URL'));
        if (pageUrl) {
            $urlRow.append($('<div>').append(
                $('<a class="brd-url-link" target="_blank" rel="noopener noreferrer">').attr('href', pageUrl).text(pageUrl)
            ));
        } else {
            $urlRow.append($('<div class="brd-val">').text('\u2014'));
        }
        $tg.append($urlRow);

        var $brRow = $('<div class="brd-meta-item" style="grid-column:1/-1;">')
            .append($('<label>').text('Browser / Environment'))
            .append(browserInfo
                ? $('<div class="brd-text-box" style="margin-top:4px;">').text(browserInfo)
                : $('<div class="brd-val">').text('\u2014'));
        $tg.append($brRow);
        $s4.append($tg);
        $body.append($s4);

        /* 5. Screenshot */
        if (screenshotUrl) {
            var $wrap = $('<div class="brd-screenshot-wrap">');
            $wrap.append(
                $('<a target="_blank" rel="noopener noreferrer">').attr('href', screenshotUrl)
                    .append($('<img alt="Screenshot" style="max-width:100%;">').attr('src', screenshotUrl))
            );
            $body.append($section('Screenshot').append($wrap));
        }

        /* 6. Admin Notes */
        if (adminNotes) {
            $body.append($section('Admin Notes').append($('<div class="brd-notes-box">').text(adminNotes)));
        }
    }

    $(document).ready(function () {
        $(document).on('click', '.btn-report-details', function () {
            buildModal($(this).data('rid'));
            $('#bugDetailModal').modal('show');
        });
    });

})(jQuery);
/*]]>*/
</script>
