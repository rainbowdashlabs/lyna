package de.chojo.lyna.mail;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class PayPalMailTest {

    @Test
    void extractMail() {
        Assertions.assertEquals("testing@eldoria.de", PayPalMail.extractMail(read("/mail.html")).get());
    }

    @Test
    void extractName() {
        Assertions.assertEquals("Eldoria", PayPalMail.extractName(read("/mail.html")).get());
    }

    @Test
    void extractProduct() {
        Assertions.assertEquals("Schematic Brush Reborn 2 - Schematic pasting reinvented", PayPalMail.extractProduct(read("/mail.html")).get());

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
