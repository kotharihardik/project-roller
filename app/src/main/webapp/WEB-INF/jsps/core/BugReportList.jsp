<%-- filepath: app/src/main/webapp/WEB-INF/jsps/core/BugReportList.jsp --%>
<%@ include file="/WEB-INF/jsps/taglibs-struts2.jsp" %>

<p class="pagetip">
    Track all the bug reports you have submitted. Use the button below to file a new one.
</p>

<s:actionerror/>
<s:actionmessage/>

<%-- ═════════════════════════  Toolbar  ════════════════════════════════ --%>
<div style="margin-bottom:16px; display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
    <a href="<s:url namespace='/roller-ui' action='bugReport'/>" class="btn btn-primary btn-sm">
        <span class="glyphicon glyphicon-plus-sign" aria-hidden="true"></span>
        &nbsp;<s:text name="bugReport.nav.reportBug" />
    </a>
</div>

<%-- ═════════════════════════  Empty state  ════════════════════════════ --%>
<s:if test="reports.empty">
    <div class="well well-lg text-center" style="color:#888; margin-top:30px;">
        <span class="glyphicon glyphicon-inbox" style="font-size:48px; display:block; margin-bottom:12px;"></span>
        <strong><s:text name="bugReport.empty" /></strong>
        <p style="margin-top:8px;">
            <a href="<s:url namespace='/roller-ui' action='bugReport'/>" class="btn btn-primary btn-sm">
                <s:text name="bugReport.nav.reportBug" />
            </a>
        </p>
    </div>
</s:if>
<s:else>

    <%-- ═════════════  Bug reports table  ══════════════════════════════ --%>
    <div class="table-responsive">
        <table class="table table-bordered table-striped table-hover rollertable" style="margin-bottom:6px;">
            <thead>
                <tr style="background:#f5f5f5;">
                    <th style="width:35%;"><s:text name="bugReport.field.title"/></th>
                    <th style="width:15%;"><s:text name="bugReport.field.category"/></th>
                    <th style="width:10%; text-align:center;"><s:text name="bugReport.field.severity"/></th>
                    <th style="width:10%; text-align:center;"><s:text name="generic.status"/></th>
                    <th style="width:12%; text-align:center;"><s:text name="generic.date"/></th>
                    <th style="width:8%;  text-align:center;"><s:text name="generic.actions"/></th>
                </tr>
            </thead>
            <tbody>
                <s:iterator value="reports" var="report">
                <tr>
                    <%-- Title --%>
                    <td style="vertical-align:middle;">
                        <strong><s:property value="#report.title"/></strong>
                    </td>

                    <%-- Category --%>
                    <td style="vertical-align:middle;">
                        <span class="label label-default">
                            <s:property value="#report.category"/>
                        </span>
                    </td>

                    <%-- Severity badge --%>
                    <td style="vertical-align:middle; text-align:center;">
                        <s:if test="#report.severity == 'CRITICAL'">
                            <span class="label label-danger">
                                <span class="glyphicon glyphicon-remove-circle" aria-hidden="true"></span>
                                &nbsp;<s:property value="#report.severity"/>
                            </span>
                        </s:if>
                        <s:elseif test="#report.severity == 'HIGH'">
                            <span class="label label-warning">
                                <span class="glyphicon glyphicon-exclamation-sign" aria-hidden="true"></span>
                                &nbsp;<s:property value="#report.severity"/>
                            </span>
                        </s:elseif>
                        <s:elseif test="#report.severity == 'MEDIUM'">
                            <span class="label label-info">
                                <span class="glyphicon glyphicon-info-sign" aria-hidden="true"></span>
                                &nbsp;<s:property value="#report.severity"/>
                            </span>
                        </s:elseif>
                        <s:else>
                            <span class="label label-default">
                                <s:property value="#report.severity"/>
                            </span>
                        </s:else>
                    </td>

                    <%-- Status badge --%>
                    <td style="vertical-align:middle; text-align:center;">
                        <s:if test="#report.status == 'OPEN'">
                            <span class="label label-primary">
                                <span class="glyphicon glyphicon-record" aria-hidden="true"></span>
                                &nbsp;<s:property value="#report.status"/>
                            </span>
                        </s:if>
                        <s:elseif test="#report.status == 'IN_PROGRESS'">
                            <span class="label label-warning">
                                <span class="glyphicon glyphicon-cog" aria-hidden="true"></span>
                                &nbsp;<s:property value="#report.status"/>
                            </span>
                        </s:elseif>
                        <s:elseif test="#report.status == 'RESOLVED'">
                            <span class="label label-success">
                                <span class="glyphicon glyphicon-ok-circle" aria-hidden="true"></span>
                                &nbsp;<s:property value="#report.status"/>
                            </span>
                        </s:elseif>
                        <s:elseif test="#report.status == 'CLOSED'">
                            <span class="label label-default">
                                <span class="glyphicon glyphicon-ban-circle" aria-hidden="true"></span>
                                &nbsp;<s:property value="#report.status"/>
                            </span>
                        </s:elseif>
                        <s:else>
                            <span class="label label-default">
                                <s:property value="#report.status"/>
                            </span>
                        </s:else>
                    </td>

                    <%-- Date --%>
                    <td style="vertical-align:middle; text-align:center; white-space:nowrap;">
                        <s:date name="#report.createdAt" format="dd MMM yyyy"/>
                    </td>

                    <%-- Actions --%>
                    <td style="vertical-align:middle; text-align:center;">
                        <s:form namespace="/roller-ui"
                                action="bugReportList"
                                method="post"
                                theme="simple"
                                cssStyle="display:inline;margin:0;">
                            <s:hidden name="reportId" value="%{#report.id}"/>
                            <s:hidden name="page"     value="%{page}"/>
                            <s:submit method="deleteReport"
                                      value="%{getText('generic.delete')}"
                                      cssClass="btn btn-danger btn-xs"
                                      onclick="return confirm('Are you sure you want to delete this bug report?');"/>
                        </s:form>
                    </td>
                </tr>
                </s:iterator>
            </tbody>
        </table>
    </div><%-- /table-responsive --%>

    <input type="hidden" id="deleteConfirmMsg" value="" />

    <%-- ═════════════  Pagination  ══════════════════════════════════════ --%>
    <div class="text-center" style="margin-top:16px;">
        <ul class="pager">
            <s:if test="page > 0">
                <s:url var="prevUrl" namespace="/roller-ui" action="bugReportList">
                    <s:param name="page" value="page - 1"/>
                </s:url>
                <li class="previous">
                    <a href='<s:property value="prevUrl"/>'>
                        <span aria-hidden="true">&larr;</span>
                        &nbsp;<s:text name="generic.previous"/>
                    </a>
                </li>
            </s:if>
            <s:if test="hasMore">
                <s:url var="nextUrl" namespace="/roller-ui" action="bugReportList">
                    <s:param name="page" value="page + 1"/>
                </s:url>
                <li class="next">
                    <a href='<s:property value="nextUrl"/>'>
                        <s:text name="generic.next"/>&nbsp;
                        <span aria-hidden="true">&rarr;</span>
                    </a>
                </li>
            </s:if>
        </ul>
    </div>

</s:else>