package de.chojo.lyna.mail;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PayPalMail {
    private final static Pattern MAIL_NAME = Pattern.compile("<br\\s?/><span>(?<name>.+?)<br/></span><span>(?<mail>.+?@.+?\\..+?)<br/>");
    private static final Pattern PRODUCT = Pattern.compile("Purchase Resource: (?<name>.+) \\(");
    public static Optional<String> extractMail(String html) {
        Matcher matcher = MAIL_NAME.matcher(html);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group("mail"));
        }
        return Optional.empty();
    }

    public static Optional<String> extractName(String html) {
        Matcher matcher = MAIL_NAME.matcher(html);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group("name"));
        }
        return Optional.empty();
    }

    public static Optional<String> extractProduct(String html) {
        Matcher matcher = PRODUCT.matcher(html);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group("name"));
        }
        return Optional.empty();
    }
}
