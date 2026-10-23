/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.configuration.elements.mailing;

import java.util.Properties;

/**
 * One mail server: where it is and whether it is reached over TLS.
 *
 * <p>The port defaults to the TLS port of the protocol the setting is for, since a server reached
 * in the clear is the exception today.
 */
public class MailSettings {
    private String host = "";
    private int port;
    private boolean ssl = true;

    private MailSettings() {
        this(465);
    }

    private MailSettings(int port) {
        this.port = port;
    }

    /**
     * @return settings for an SMTP server, on the SMTPS port
     */
    public static MailSettings smtp() {
        return new MailSettings(465);
    }

    /**
     * @return settings for an IMAP server, on the IMAPS port
     */
    public static MailSettings imap() {
        return new MailSettings(993);
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public boolean ssl() {
        return ssl;
    }

    public Properties properties(String prefix) {
        Properties props = new Properties();
        props.put("mail.%s.host".formatted(prefix), host());
        props.put("mail.%s.port".formatted(prefix), port());
        props.put("mail.%s.ssl.enable".formatted(prefix), ssl());
        return props;
    }
}
