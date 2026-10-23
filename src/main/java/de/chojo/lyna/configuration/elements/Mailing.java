/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.configuration.elements;

import de.chojo.lyna.configuration.elements.mailing.MailSettings;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;
import dev.chojo.ocular.override.Prop;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
@OverwritePrefix("MAIL")
public class Mailing {
    private MailSettings smtp = MailSettings.smtp();
    private MailSettings imap = MailSettings.imap();

    @Overwrite(env = @Env, prop = @Prop)
    private String user = "";

    @Overwrite(env = @Env, prop = @Prop)
    private String password = "";
    /**
     * The addresses trusted to forward a payment receipt here.
     *
     * <p>Empty trusts nobody, which is why a forwarded mail is refused until this is set. It used to
     * default to a list holding the empty string, and every address contains that - so the check
     * read as though it were being made and passed whatever it was given.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private List<String> originMail = List.of();
    /**
     * Takes any sender's mail as a payment receipt instead of only the addresses that are trusted.
     *
     * <p>For working on the mail handlers locally, where a receipt is something you send yourself.
     * An instance doing this in earnest would act on a receipt anybody could forge.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private boolean skipVerify = false;

    @Overwrite(env = @Env, prop = @Prop)
    private int pollSeconds = 300;

    @Overwrite(env = @Env, prop = @Prop)
    private boolean enabled = false;
    /**
     * The senders PayPal writes receipts from. PayPal sends from an address of the account's
     * country, so the list is a setting rather than one address in the code.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private List<String> paypalSenders = List.of("service@paypal.de", "service@paypal.com");

    /**
     * The name the instance signs its mail with, in the header, the footer and the subjects that
     * name it.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private String senderName = "Lyna";

    /**
     * Further Jakarta Mail properties, over the ones the settings above produce. Read from the file
     * only: a map has no single environment value.
     */
    private Map<String, String> properties = Collections.emptyMap();

    public boolean skipVerify() {
        return skipVerify;
    }

    public int pollSeconds() {
        return pollSeconds;
    }

    /**
     * Whether mail is polled and sent at all.
     *
     * <p>Off, nothing connects to the mail server and nothing is sent. A deployment that grants its
     * licenses through Ko-fi or the bot needs no mailbox, and the end-to-end stack has none.
     *
     * @return whether to reach the mail server
     */
    public boolean enabled() {
        return enabled;
    }

    public String senderName() {
        return senderName;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public List<String> originMails() {
        return originMail;
    }

    public MailSettings smtp() {
        return smtp;
    }

    public MailSettings imap() {
        return imap;
    }

    public List<String> paypalSenders() {
        return paypalSenders;
    }

    public Properties properties() {
        Properties props = new Properties();
        props.putAll(smtp().properties("smtp"));
        props.putAll(imap().properties("imap"));
        props.putAll(properties);
        return props;
    }
}
