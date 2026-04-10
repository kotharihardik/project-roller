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

package org.apache.roller.weblogger.business;

import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.business.startup.StartupException;
import org.apache.roller.weblogger.config.WebloggerConfig;


/**
 * Encapsulates Roller mail configuration, returns mail sessions.
 */
public class MailProvider {

    private static final Log LOG = LogFactory.getLog(MailProvider.class);

    private enum ConfigurationType { JNDI_NAME, MAIL_PROPERTIES }

    private Session session = null;

    private ConfigurationType type = ConfigurationType.JNDI_NAME;

    private String mailHostname = null;
    private int    mailPort     = -1;
    private String mailUsername = null;
    private String mailPassword = null;


    public MailProvider() throws StartupException {

        String connectionTypeString = WebloggerConfig.getProperty("mail.configurationType");
        if ("properties".equals(connectionTypeString)) {
            type = ConfigurationType.MAIL_PROPERTIES;
        }

        String jndiName  = WebloggerConfig.getProperty("mail.jndi.name");
        mailHostname     = WebloggerConfig.getProperty("mail.hostname");
        mailUsername     = WebloggerConfig.getProperty("mail.username");

        // Strip surrounding quotes if someone wrote password="abc" in properties
        mailPassword     = WebloggerConfig.getProperty("mail.password");
        if (mailPassword != null) {
            mailPassword = mailPassword.trim().replace("\"", "");
        }

        try {
            String portString = WebloggerConfig.getProperty("mail.port");
            if (portString != null && !portString.trim().isEmpty()) {
                mailPort = Integer.parseInt(portString.trim());
            }
        } catch (Exception e) {
            LOG.warn("mail server port not a valid integer, ignoring");
        }

        // ----------------------------------------------------------------
        // Init session
        // ----------------------------------------------------------------
        if (type == ConfigurationType.JNDI_NAME) {
            if (jndiName != null && !jndiName.startsWith("java:")) {
                jndiName = "java:comp/env/" + jndiName;
            }
            try {
                Context ctx = new InitialContext();
                session = (Session) ctx.lookup(jndiName);
            } catch (NamingException ex) {
                throw new StartupException(
                    "ERROR looking up mail-session with JNDI name: " + jndiName);
            }

        } else {
            // -------------------------------------------------------
            // PROPERTIES mode — build full JavaMail props for Gmail
            // -------------------------------------------------------
            Properties props = new Properties();

            props.setProperty("mail.smtp.host", mailHostname);

            if (mailPort != -1) {
                props.setProperty("mail.smtp.port", String.valueOf(mailPort));
            }

            // FIX 1: STARTTLS — read from config, default true for port 587
            String starttls = WebloggerConfig.getProperty("mail.smtp.starttls.enable");
            if (starttls != null && !starttls.trim().isEmpty()) {
                props.setProperty("mail.smtp.starttls.enable", starttls.trim());
            } else if (mailPort == 587) {
                props.setProperty("mail.smtp.starttls.enable", "true");
            }

            // SSL trust — avoids SSL handshake errors
            String sslTrust = WebloggerConfig.getProperty("mail.smtp.ssl.trust");
            if (sslTrust != null && !sslTrust.trim().isEmpty()) {
                props.setProperty("mail.smtp.ssl.trust", sslTrust.trim());
            } else {
                // default trust the configured host
                props.setProperty("mail.smtp.ssl.trust", mailHostname);
            }

            // TLS protocol version
            String sslProto = WebloggerConfig.getProperty("mail.smtp.ssl.protocols");
            if (sslProto != null && !sslProto.trim().isEmpty()) {
                props.setProperty("mail.smtp.ssl.protocols", sslProto.trim());
            } else {
                props.setProperty("mail.smtp.ssl.protocols", "TLSv1.2");
            }

            // Auth flag
            if (mailUsername != null && mailPassword != null
                    && !mailUsername.isEmpty() && !mailPassword.isEmpty()) {
                props.setProperty("mail.smtp.auth", "true");
            }

            // Debug
            String debug = WebloggerConfig.getProperty("mail.debug");
            if ("true".equals(debug)) {
                props.setProperty("mail.debug", "true");
            }

            LOG.info("[MailProvider] SMTP props: " + props);
            System.out.println("[MailProvider] SMTP props: " + props);

            // FIX 2: Session.getInstance() with Authenticator
            // Session.getDefaultInstance() ignores STARTTLS — NEVER use it for Gmail
            if (mailUsername != null && !mailUsername.isEmpty()
                    && mailPassword != null && !mailPassword.isEmpty()) {
                final String user = mailUsername;
                final String pass = mailPassword;
                session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, pass);
                    }
                });
            } else {
                session = Session.getInstance(props, null);
            }
        }

        // ----------------------------------------------------------------
        // Fail-fast: verify we can connect at startup
        // ----------------------------------------------------------------
        try {
            Transport transport = getTransport();
            transport.close();
        } catch (Exception e) {
            throw new StartupException("ERROR connecting to mail server", e);
        }
    }


    /**
     * Get a mail Session.
     */
    public Session getSession() {
        return session;
    }


    /**
     * Create and connect to transport, caller is responsible for closing transport.
     */
    public Transport getTransport() throws MessagingException {
        Transport transport;

        if (type == ConfigurationType.MAIL_PROPERTIES) {
            // Session already has Authenticator — just get transport and connect
            // Do NOT pass credentials again here; Authenticator handles it
            transport = session.getTransport("smtp");
            transport.connect();
        } else {
            transport = session.getTransport();
            transport.connect();
        }

        return transport;
    }
}