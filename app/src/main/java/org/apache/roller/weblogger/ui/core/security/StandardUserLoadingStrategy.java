package org.apache.roller.weblogger.ui.core.security;

import java.util.ArrayList;
import java.util.List;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.pojos.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

//  Handles standard username/password login.

public class StandardUserLoadingStrategy {
    
    public UserDetails loadUser(String userName, UserManager userManager) throws WebloggerException {
        
        User user = userManager.getUserByUserName(userName);
        
        if (user == null) {
            throw new UsernameNotFoundException("ERROR no user: " + userName);
        }
        
        // Build authorities from user roles
        List<String> roles = userManager.getRoles(user);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(roles.size());
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        
        return new org.springframework.security.core.userdetails.User(
                user.getUserName(), 
                user.getPassword(),
                true, true, true, true, 
                authorities);
    }
}
