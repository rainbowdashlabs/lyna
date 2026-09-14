package de.chojo.lyna.service;

import com.sun.net.httpserver.HttpServer;
import de.chojo.lyna.web.api.admin.IconUrls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an operator is allowed to point a product tile at, checked against a server that really
 * answers rather than against a mocked client: the point of the check is what the address on the
 * far end says, and a mock would only repeat what the test already assumed.
 */
class IconUrlServiceTest {
    private HttpServer server;
    private IconUrls iconUrls;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/icon.png", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/page.html", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/untyped", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        iconUrls = new IconUrls();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    @DisplayName("An address serving an image is accepted")
    void imageIsAccepted() {
        assertTrue(iconUrls.reject(url("/icon.png")).isEmpty());
    }

    @Test
    @DisplayName("An address serving a page is not an icon")
    void nonImageIsRejected() {
        assertEquals(Optional.of("That address does not serve an image"), iconUrls.reject(url("/page.html")));
    }

    @Test
    @DisplayName("An address that says nothing about what it serves is not taken on trust")
    void missingContentTypeIsRejected() {
        assertTrue(iconUrls.reject(url("/untyped")).isPresent());
    }

    @Test
    @DisplayName("An address that answers with a failure is reported with its status")
    void notFoundIsRejected() {
        assertEquals(Optional.of("That address answered 404"), iconUrls.reject(url("/missing")));
    }

    @Test
    @DisplayName("Nothing listening at the address is reported rather than stored")
    void unreachableIsRejected() {
        assertEquals(Optional.of("Nothing answered at that address"),
                iconUrls.reject("http://127.0.0.1:1/icon.png"));
    }

    @Test
    @DisplayName("Only http and https may be pointed at")
    void otherSchemesAreRejected() {
        assertEquals(Optional.of("The address has to be http or https"), iconUrls.reject("ftp://example.invalid/i.png"));
        assertEquals(Optional.of("The address has to be http or https"), iconUrls.reject("/local/path.png"));
    }

    @Test
    @DisplayName("Something that is not an address at all is refused before anything is fetched")
    void malformedIsRejected() {
        assertEquals(Optional.of("That is not an address"), iconUrls.reject("http://exa mple.invalid/i.png"));
    }
}
