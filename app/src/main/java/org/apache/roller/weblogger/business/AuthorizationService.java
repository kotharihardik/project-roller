package org.apache.roller.weblogger.business;

import java.util.List;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.pojos.Weblog;

public interface AuthorizationService {
    boolean hasGlobalPermission(User user, String action);

    boolean hasGlobalPermissions(User user, List<String> actions);

    boolean hasUserPermission(User user, Weblog weblog, String action);

    boolean hasUserPermissions(User user, Weblog weblog, List<String> actions);
}
