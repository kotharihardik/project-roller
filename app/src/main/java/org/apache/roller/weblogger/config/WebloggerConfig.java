/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.util.PropertyExpander;


/**
 * This is the single entry point for accessing configuration properties in Roller.
 */
public final class WebloggerConfig {
    
    private final static String default_config = "/org/apache/roller/weblogger/config/roller.properties";
    private final static String custom_config = "/roller-custom.properties";
    private final static String junit_config = "/roller-junit.properties";
    private final static String custom_jvm_param = "roller.custom.config";

    private final static Properties config;
    private static volatile Map<String, String> dotEnvCache;

    private final static Log log;
    

    /*
     * Static block run once at class loading
     *
     * We load the default properties and any custom properties we find
     */
    static {
        config = new Properties();

        try {
            // we'll need this to get at our properties files in the classpath
            Class<?> configClass = Class.forName("org.apache.roller.weblogger.config.WebloggerConfig");

            // first, lets load our default properties
            try (InputStream is = configClass.getResourceAsStream(default_config)) {
                config.load(is);
            }
            
            // first, see if we can find our junit testing config
            try (InputStream test = configClass.getResourceAsStream(junit_config)) {
                
                if (test != null) {

                    config.load(test);
                    System.out.println("Roller Weblogger: Successfully loaded junit properties file from classpath");
                    System.out.println("File path : " + configClass.getResource(junit_config).getFile());

                } else {

                    // now, see if we can find our custom config
                    try(InputStream custom = configClass.getResourceAsStream(custom_config)) {

                        if (custom != null) {
                            config.load(custom);
                            System.out.println("Roller Weblogger: Successfully loaded custom properties file from classpath");
                            System.out.println("File path : " + configClass.getResource(custom_config).getFile());
                        } else {
                            System.out.println("Roller Weblogger: No custom properties file found in classpath");
                        }

                        System.out.println("(To run eclipse junit local tests see docs/testing/roller-junit.properties)");
                    }
                }
            }

            // finally, check for an external config file
            String env_file = System.getProperty(custom_jvm_param);
            if(env_file != null && env_file.length() > 0) {
                File custom_config_file = new File(env_file);

                // make sure the file exists, then try and load it
                if(custom_config_file.exists()) {
                    try(InputStream is = new FileInputStream(custom_config_file)) {
                        config.load(is);
                    }
                    System.out.println("Roller Weblogger: Successfully loaded custom properties from "+
                            custom_config_file.getAbsolutePath());
                } else {
                    System.out.println("Roller Weblogger: Failed to load custom properties from "+
                            custom_config_file.getAbsolutePath());
                }

            } 

            // Now expand system properties for properties in the config.expandedProperties list,
            // replacing them by their expanded values.
            String expandedPropertiesDef = config.getProperty("config.expandedProperties");
            if (expandedPropertiesDef != null) {
                String[] expandedProperties = expandedPropertiesDef.split(",");
                for (int i = 0; i < expandedProperties.length; i++) {
                    String propName = expandedProperties[i].trim();
                    String initialValue = config.getProperty(propName);
                    if (initialValue != null) {
                        String expandedValue = PropertyExpander.expandSystemProperties(initialValue);
                        config.setProperty(propName, expandedValue);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // tell log4j2 to use the optionally specified config file instead of Roller's,
        // but only if it hasn't already been set with -D at JVM startup.
        String log4j2ConfigKey = "log4j.configurationFile";
        String customLog4j2File = config.getProperty(log4j2ConfigKey);
        if(customLog4j2File != null && !customLog4j2File.isBlank() && System.getProperty(log4j2ConfigKey) == null) {
            System.setProperty(log4j2ConfigKey, customLog4j2File);
        }
        
        // this bridges java.util.logging -> SLF4J which ends up being log4j2, probably.
        org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
        org.slf4j.bridge.SLF4JBridgeHandler.install();
        
        // finally we can start logging...
        log = LogFactory.getLog(WebloggerConfig.class);

        // some debugging for those that want it
        if(log.isDebugEnabled()) {
            log.debug("WebloggerConfig looks like this ...");

            String key;
            Enumeration<Object> keys = config.keys();
            while(keys.hasMoreElements()) {
                key = (String) keys.nextElement();
                log.debug(key+"="+config.getProperty(key));
            }
        }

    }


    // no, you may not instantiate this class :p
    private WebloggerConfig() {}

    /**
     * Loads Roller's configuration.
     * Call this as early as possible in the lifecycle.
     */
    public static void init() {
        // triggers static block
    }    

    private static Map<String, String> loadDotEnv() {
        if (dotEnvCache != null) {
            return dotEnvCache;
        }
        synchronized (WebloggerConfig.class) {
            if (dotEnvCache != null) {
                return dotEnvCache;
            }

            Map<String, String> env = new HashMap<>();
            String userDir = System.getProperty("user.dir", "");
            String catalinaBase = System.getProperty("catalina.base", "");
            String[] candidates = {
                    userDir + "/.env",
                    userDir + "/../.env",
                    catalinaBase + "/.env",
                    catalinaBase + "/../../.env"
            };

            for (String candidate : candidates) {
                if (candidate == null || candidate.isEmpty()) {
                    continue;
                }
                Path path = Paths.get(candidate).normalize();
                if (!Files.exists(path)) {
                    continue;
                }
                try {
                    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        int eq = trimmed.indexOf('=');
                        if (eq <= 0) {
                            continue;
                        }
                        String key = trimmed.substring(0, eq).trim();
                        String val = trimmed.substring(eq + 1).trim();
                        if (val.length() >= 2 &&
                                ((val.charAt(0) == '"' && val.charAt(val.length() - 1) == '"') ||
                                 (val.charAt(0) == '\'' && val.charAt(val.length() - 1) == '\''))) {
                            val = val.substring(1, val.length() - 1);
                        }
                        env.putIfAbsent(key, val);
                    }
                    log.info("WebloggerConfig: loaded .env from " + path.toAbsolutePath());
                    break;
                } catch (IOException e) {
                    log.warn("WebloggerConfig: failed to read .env from " + path + ": " + e.getMessage());
                }
            }
            dotEnvCache = env;
        }
        return dotEnvCache;
    }

    private static String getEnvOrDotEnv(String key) {
        String env = System.getenv(key);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        String dot = loadDotEnv().get(key);
        return (dot != null && !dot.trim().isEmpty()) ? dot.trim() : null;
    }

    private static String resolveEnvVars(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            String envVarName = token.startsWith("env.") ? token.substring(4) : token;
            String envVal = getEnvOrDotEnv(envVarName);
            matcher.appendReplacement(sb,
                java.util.regex.Matcher.quoteReplacement(envVal != null ? envVal : matcher.group(0)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Retrieve a property value
     * @param     key Name of the property
     * @return    String Value of property requested, null if not found
     */
    public static String getProperty(String key) {
        String value = config.getProperty(key);
        log.debug("Fetching property ["+key+"="+value+"]");
        return value == null ? null : resolveEnvVars(value.trim());
    }
    
    /**
     * Retrieve a property value
     * @param     key Name of the property
     * @param     defaultValue Default value of property if not found     
     * @return    String Value of property requested or defaultValue
     */
    public static String getProperty(String key, String defaultValue) {
        String value = config.getProperty(key);
        log.debug("Fetching property ["+key+"="+value+",defaultValue="+defaultValue+"]");
        if (value == null) {
            return defaultValue;
        }
        return resolveEnvVars(value.trim());
    }

    /**
     * Retrieve a property as a boolean ... defaults to false if not present.
     */
    public static boolean getBooleanProperty(String name) {
        return getBooleanProperty(name,false);
    }

    /**
     * Retrieve a property as a boolean ... with specified default if not present.
     */
    public static boolean getBooleanProperty(String name, boolean defaultValue) {
        // get the value first, then convert
        String value = WebloggerConfig.getProperty(name);

        if(value == null) {
            return defaultValue;
        }

        return Boolean.valueOf(value);
    }

    /**
     * Retrieve a property as an int ... defaults to 0 if not present.
     */
    public static int getIntProperty(String name) {
        return getIntProperty(name, 0);
    }

    /**
     * Retrieve a property as a int ... with specified default if not present.
     */
    public static int getIntProperty(String name, int defaultValue) {
        // get the value first, then convert
        String value = WebloggerConfig.getProperty(name);

        if (value == null) {
            return defaultValue;
        }

        return Integer.valueOf(value);
    }

    /**
     * Retrieve all property keys
     * @return Enumeration A list of all keys
     **/
    public static Enumeration<Object> keys() {
        return config.keys();
    } 

    /**
     * Set the "uploads.dir" property at runtime.
     * <p />
     * Properties are meant to be read-only, but we make this exception because  
     * we know that some people are still writing their uploads to the webapp  
     * context and we can only get that path at runtime (and for unit testing).
     * <p />
     * This property is *not* persisted in any way.
     */
    public static void setUploadsDir(String path) {
        // only do this if the user wants to use the webapp context
        if("${webapp.context}".equals(config.getProperty("uploads.dir"))) {
            config.setProperty("uploads.dir", path);
        }
    }
    
    /**
     * Set the "themes.dir" property at runtime.
     * <p />
     * Properties are meant to be read-only, but we make this exception because  
     * we know that some people are still using their themes in the webapp  
     * context and we can only get that path at runtime (and for unit testing).
     * <p />
     * This property is *not* persisted in any way.
     */
    public static void setThemesDir(String path) {
        // only do this if the user wants to use the webapp context
        if("${webapp.context}".equals(config.getProperty("themes.dir"))) {
            config.setProperty("themes.dir", path);
        }
    }

    /**
     * Return the value of the authentication.method property as an AuthMethod
     * enum value.  Matching is done by checking the propertyName of each AuthMethod
     * enum object.
     * <p />
     * @throws IllegalArgumentException if property value defined in the properties
     * file is missing or not the property name of any AuthMethod enum object.
     */
    public static AuthMethod getAuthMethod() {
        return AuthMethod.getAuthMethod(getProperty("authentication.method"));
    }
    
}