package org.apache.roller.weblogger.business;

import java.util.Collections;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.pojos.GlobalPermission;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.pojos.WeblogPermission;

public class AuthorizationServiceImpl implements AuthorizationService {

    private static final Log log = LogFactory.getLog(AuthorizationServiceImpl.class);

    @Override
    public boolean hasGlobalPermission(User user, String action) {
        return hasGlobalPermissions(user, Collections.singletonList(action));
    }

    @Override
    public boolean hasGlobalPermissions(User user, List<String> actions) {
        try {
            GlobalPermission permission = new GlobalPermission(actions);
            return WebloggerFactory.getWeblogger().getUserManager().checkPermission(permission, user);
        } catch (WebloggerException ex) {
            return false;
        }
    }

    @Override
    public boolean hasUserPermission(User user, Weblog weblog, String action) {
        return hasUserPermissions(user, weblog, Collections.singletonList(action));
    }

    @Override
    public boolean hasUserPermissions(User user, Weblog weblog, List<String> actions) {
        try {
            UserManager userManager = WebloggerFactory.getWeblogger().getUserManager();
            WeblogPermission weblogPermission = new WeblogPermission(weblog, user, actions);
            return userManager.checkPermission(weblogPermission, user);
        } catch (WebloggerException ex) {
            log.error("ERROR checking user permission", ex);
        }
        return false;
    }
}
