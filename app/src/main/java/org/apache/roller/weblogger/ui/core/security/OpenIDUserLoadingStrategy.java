package org.apache.roller.weblogger.ui.core.security;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.pojos.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// Handles OpenID URL-based login.

public class OpenIDUserLoadingStrategy {
    
    private static final Log log = LogFactory.getLog(OpenIDUserLoadingStrategy.class);
    
    public UserDetails loadUser(String userName, UserManager userManager) throws WebloggerException {
        
        // Remove trailing slash from URL
        String openIdUrl = userName;
        if (openIdUrl.endsWith("/")) {
            openIdUrl = openIdUrl.substring(0, openIdUrl.length() - 1);
        }
        
        User user = userManager.getUserByOpenIdUrl(openIdUrl);
        
        String name;
        String password;
        List<SimpleGrantedAuthority> authorities;
        
        if (user == null) 
        {
            // User not found - return default for OpenID registration flow
            log.warn("No user found with OpenID URL: " + openIdUrl);
            authorities = new ArrayList<>(1);
            authorities.add(new SimpleGrantedAuthority("rollerOpenidLogin"));
            name = "openid";
            password = "openid";
        } 
        else 
        {
            // User found - build authorities from roles
            List<String> roles = userManager.getRoles(user);
            authorities = new ArrayList<>(roles.size());
            for (String role : roles) 
            {
                authorities.add(new SimpleGrantedAuthority(role));
            }
            name = user.getUserName();
            password = user.getPassword();
        }
        
        return new org.springframework.security.core.userdetails.User(
                name, password,
                true, true, true, true, 
                authorities);
    }
}
