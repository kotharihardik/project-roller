package org.apache.roller.weblogger.business.url;

import java.util.Map;

public interface AuthURLStrategy {
    
    String getLoginURL(boolean absolute);
    String getLogoutURL(boolean absolute);
    String getRegisterURL(boolean absolute);
    String getActionURL(String action, String namespace, String weblogHandle, Map<String, String> parameters, boolean absolute);
    String getEntryAddURL(String weblogHandle, boolean absolute);
    String getEntryEditURL(String weblogHandle, String entryId, boolean absolute);
    String getWeblogConfigURL(String weblogHandle, boolean absolute);
    String getXmlrpcURL(boolean absolute);
    String getAtomProtocolURL(boolean absolute);
    String getOAuthRequestTokenURL();
    String getOAuthAuthorizationURL();
    String getOAuthAccessTokenURL();
}
