package org.apache.roller.weblogger.ui.struts2.core;

import javax.servlet.http.HttpServletRequest;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.StarService;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.pojos.RollerStar;
import org.apache.roller.weblogger.ui.struts2.util.UIAction;
import org.apache.struts2.ServletActionContext;

public class StarAction extends UIAction {

    private static final long serialVersionUID = 1L;

    private String targetId;
    private String targetType; // WEBLOG or ENTRY
    private String returnTo;

    public StarAction() {
        this.actionName = "starAction";
    }

    @Override
    public boolean isWeblogRequired() {
        return false;
    }

    @Override
    public String execute() {
        return SUCCESS;
    }

    public String star() {
        try {
            StarService service = WebloggerFactory.getWeblogger().getStarService();
            service.star(getAuthenticatedUser().getId(), RollerStar.TargetType.valueOf(targetType), targetId);
            WebloggerFactory.getWeblogger().flush();
        } catch (WebloggerException | IllegalArgumentException e) {
            addError("star.error");
        }
        return SUCCESS;
    }

    public String unstar() {
        try {
            StarService service = WebloggerFactory.getWeblogger().getStarService();
            service.unstar(getAuthenticatedUser().getId(), RollerStar.TargetType.valueOf(targetType), targetId);
            WebloggerFactory.getWeblogger().flush();
        } catch (WebloggerException | IllegalArgumentException e) {
            addError("star.error");
        }
        return SUCCESS;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getReturnTo() {
        String normalized = normalizeReturnTo(returnTo);
        if (normalized != null) {
            return normalized;
        }

        HttpServletRequest request = ServletActionContext.getRequest();
        if (request != null) {
            normalized = normalizeReturnTo(request.getHeader("Referer"));
            if (normalized != null) {
                return normalized;
            }
        }

        return "/";
    }

    public void setReturnTo(String returnTo) {
        this.returnTo = normalizeReturnTo(returnTo);
    }

    private String normalizeReturnTo(String returnTo) {
        if (returnTo == null) {
            return null;
        }

        String normalized = returnTo.trim();
        if (normalized.isEmpty() || normalized.contains("$")) {
            return null;
        }

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }

        String contextPath = getSiteURL();
        if (normalized.equals(contextPath)) {
            return "/";
        }
        if (normalized.startsWith(contextPath + "/")) {
            normalized = normalized.substring(contextPath.length());
        }

        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        return normalized;
    }
}
