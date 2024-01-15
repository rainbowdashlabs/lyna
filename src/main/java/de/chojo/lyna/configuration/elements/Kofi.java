package de.chojo.lyna.configuration.elements;

import java.util.UUID;

public class Kofi {
    private UUID verificationToken = UUID.randomUUID();

    public UUID verificationToken() {
        return verificationToken;
    }
}
