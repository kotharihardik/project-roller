package org.apache.roller.weblogger.ui.core.security;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Spring Security UserDetailsService for Roller.
 * Delegates to separate classes for different login types.
 */
public class RollerUserDetailsService implements UserDetailsService {
    private static Log log = LogFactory.getLog(RollerUserDetailsService.class);
    
    private final OpenIDUserLoadingStrategy openIdStrategy = new OpenIDUserLoadingStrategy();
    private final StandardUserLoadingStrategy standardStrategy = new StandardUserLoadingStrategy();
    
    @Override
    public UserDetails loadUserByUsername(String userName) {
        
        Weblogger roller;
        try {
            roller = WebloggerFactory.getWeblogger();
        } catch (Exception e) {
            // Should only happen in case of 1st time startup, setup required
            log.debug("Ignorable error getting Roller instance", e);
            // Thowing a "soft" exception here allows setup to proceed
            throw new UsernameNotFoundException("User info not available yet.");
        }
        
        try {
            UserManager userManager = roller.getUserManager();
            
            // OpenID login (URL starting with http:// or https://)
            if (userName.startsWith("http://") || userName.startsWith("https://")) {
                return openIdStrategy.loadUser(userName, userManager);
            }
            
            // Standard username/password login
            return standardStrategy.loadUser(userName, userManager);
            
        } catch (WebloggerException ex) {
            throw new DataRetrievalFailureException("ERROR in user lookup", ex);
        }
    }
}
