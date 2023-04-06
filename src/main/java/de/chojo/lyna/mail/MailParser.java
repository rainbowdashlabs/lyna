package de.chojo.lyna.mail;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;

import static org.slf4j.LoggerFactory.getLogger;

public class MailParser {
    private static final Logger log = getLogger(MailParser.class);
    private final StringBuilder builder = new StringBuilder();
    private final Message message;
    private boolean parsed = false;

    public MailParser(Message message) {
        this.message = message;
    }

    public void dumpPart(Part p) throws MessagingException, IOException {
        String ct = p.getContentType();
        try {
            log.info("CONTENT-TYPE: {}", new ContentType(ct));
        } catch (ParseException pex) {
            log.warn("BAD CONTENT-TYPE: {}", ct);
        }

        /*
         * Using isMimeType to determine the content type avoids
         * fetching the actual content data until we need it.
         */
        if (p.isMimeType("text/plain")) {
            append((String) p.getContent());
        } else if (p.isMimeType("multipart/*")) {
            log.info("Found multipart");
            Multipart mp = (Multipart) p.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++) {
                dumpPart(mp.getBodyPart(i));
            }
        } else if (p.isMimeType("message/rfc822")) {
            log.info("Found nested message");
            dumpPart((Part) p.getContent());
        } else {
            var obj = p.getContent();
            if (obj instanceof String str) {
                append(str);
            } else if (obj instanceof InputStream in) {
                append(new String(in.readAllBytes()));
            } else {
                append(obj.toString());
            }
        }
    }

    private int level = 0;

    private void append(String s) {
        builder.append(s);
    }

    public String parsed() throws MessagingException, IOException {
        if (!parsed) {
            dumpPart(message);
        }
        parsed = true;
        return builder.toString();
    }
}
