package de.chojo.lyna.configuration.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.Prop;
import dev.chojo.ocular.override.OverwritePrefix;

import de.chojo.lyna.configuration.elements.mailing.MailSettings;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
@OverwritePrefix("MAIL")
public class Mailing {
    private MailSettings smtp = new MailSettings();
    private MailSettings imap = new MailSettings();
    @Overwrite(env = @Env, prop = @Prop)
    private String user = "";
    @Overwrite(env = @Env, prop = @Prop)
    private String password = "";
    @Overwrite(env = @Env, prop = @Prop)
    private List<String> originMail = java.util.List.of("");
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
    private boolean enabled = true;
    // While we do no use javamail, we use angus and both implement jakarta.mail
    // Most of the parameters of javamail can be applied here as well
    // https://www.tutorialspoint.com/javamail_api/javamail_api_imap_servers.htm
    // https://www.tutorialspoint.com/javamail_api/javamail_api_smtp_servers.htm
    @Overwrite(env = @Env, prop = @Prop)
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

    public Properties properties() {
        Properties props = new Properties();
        props.putAll(smtp().properties("smtp"));
        props.putAll(imap().properties("imap"));
        props.putAll(properties);
        return props;
    }
}
