package de.chojo.lyna.mail;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class PayPalMailTest {
    PayPalMail mail = PayPalMail.parse(read("/mail.html"));

    @Test
    void extractMail() {
        Assertions.assertEquals("testing@eldoria.de", mail.mail().get());
    }

    @Test
    void extractName() {
        Assertions.assertEquals("Eldoria", mail.name().get());
    }

    @Test
    void extractProduct() {
        Assertions.assertEquals("Schematic Brush Reborn 2 - Schematic pasting reinvented", mail.product().get());

    }

    private String read(String path) {
        try (var in = getClass().getResourceAsStream(path)) {
            if (in == null) throw new RuntimeException("File %s not in resources".formatted(path));
            return new String(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
