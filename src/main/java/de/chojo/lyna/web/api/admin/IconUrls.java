package de.chojo.lyna.web.api.admin;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Checks that an icon address really serves an image before an operator's typo becomes a broken
 * tile on the storefront.
 *
 * <p>A HEAD request rather than a GET: the answer wanted is whether the address exists and what it
 * serves, and an icon is the kind of thing a content delivery network is happy to answer that for
 * without sending the bytes.
 */
public class IconUrls {
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * @param url the address an operator typed
     * @return nothing when the address is usable, otherwise what is wrong with it
     */
    public Optional<String> reject(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return Optional.of("That is not an address");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return Optional.of("The address has to be http or https");
        }
        HttpResponse<Void> response;
        try {
            response = client.send(HttpRequest.newBuilder(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build(), HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            return Optional.of("Nothing answered at that address");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of("Nothing answered at that address");
        }
        if (response.statusCode() / 100 != 2) {
            return Optional.of("That address answered " + response.statusCode());
        }
        String contentType = response.headers().firstValue("content-type").orElse("");
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return Optional.of("That address does not serve an image");
        }
        return Optional.empty();
    }
}
