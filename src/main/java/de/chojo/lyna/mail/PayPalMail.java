package de.chojo.lyna.mail;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PayPalMail(Optional<String> mail, Optional<String> name, Optional<String> product) {
    private static final Pattern PRODUCT = Pattern.compile("Purchase Resource: (?<name>.+?) \\(.+?\\)$");

    public static PayPalMail parse(String html) {
        Optional<String> mail = extractMail(html);
        Optional<String> name = extractName(html);
        Optional<String> product = extractProduct(html);
        return new PayPalMail(mail, name, product);
    }

    private static Optional<String> extractMail(String html) {
        return getBuyer(html).map(buyer -> {
            Elements span = buyer.getElementsByTag("span");
            return span.size() < 3 ? null : span.get(2).text();
        });
    }

    private static Optional<String> extractName(String html) {
        return getBuyer(html).map(buyer -> {
            Elements span = buyer.getElementsByTag("span");
            return span.size() < 2 ? null : span.get(1).text();
        });
    }

    private static Optional<Element> getBuyer(String html) {
        List<Element> details = getCardDetails(html);
        if (details.size() < 2) return Optional.empty();
        Elements p = details.get(1).getElementsByTag("p");
        return p.size() < 3 ? Optional.empty() : Optional.ofNullable(p.get(2));
    }

    private static Optional<String> extractProduct(String html) {
        List<Element> details = getCardDetails(html);
        if (details.size() < 3) return Optional.empty();
        Elements span = details.get(2).getElementsByTag("span");
        Matcher matcher = PRODUCT.matcher(span.get(5).text());
        return matcher.find() ? Optional.ofNullable(matcher.group("name")) : Optional.empty();
    }

    private static List<Element> getCardDetails(String html) {
        return Jsoup.parse(html, Parser.htmlParser()).getElementsByTag("table").stream()
                .filter(e -> e.id().equals("cartDetails"))
                .toList();

    }
}
