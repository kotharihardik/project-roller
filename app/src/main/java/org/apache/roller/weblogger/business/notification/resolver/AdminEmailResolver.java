package org.apache.roller.weblogger.business.notification.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.pojos.GlobalPermission;
import org.apache.roller.weblogger.pojos.User;

/**
 * Resolves admin email addresses from UserManager.
 */
public class AdminEmailResolver {

    private static final Log LOG = LogFactory.getLog(AdminEmailResolver.class);
    private final UserManager userManager;

    public AdminEmailResolver(UserManager userManager) {
        this.userManager = userManager;
    }

    public List<String> resolveAdminEmails() {
        List<String> emails = new ArrayList<>();
        try {
            List<User> users = userManager.getUsers(Boolean.TRUE, null, null, 0, -1);
            if (users == null || users.isEmpty()) return emails;

            GlobalPermission adminPerm =
                new GlobalPermission(Collections.singletonList(GlobalPermission.ADMIN));

            for (User user : users) {
                if (user == null) continue;
                try {
                    if (userManager.checkPermission(adminPerm, user)) {
                        String email = user.getEmailAddress();
                        if (email != null && !email.trim().isEmpty()) {
                            emails.add(email.trim());
                        }
                    }
                } catch (WebloggerException e) {
                    LOG.warn("Permission check failed for: " + user.getUserName());
                }
            }
        } catch (WebloggerException e) {
            LOG.error("Failed to fetch users for admin email resolution", e);
        }
        LOG.debug("Resolved " + emails.size() + " admin email(s)");
        return emails;
    }
}