package de.chojo.lyna.configuration.elements;

import java.util.UUID;

/**
 * Ko-fi's webhook settings.
 */
public class Kofi {
    /**
     * The token Ko-fi sends with every webhook, which is the only thing saying the call is theirs.
     *
     * <p>Held as text rather than a {@link UUID} because that is what it is: an opaque shared secret
     * that happens to be shaped like one today. A random default means an instance nobody has
     * configured refuses every call rather than accepting a blank one.
     */
    private String verificationToken = UUID.randomUUID().toString();

    public String verificationToken() {
        return verificationToken;
    }
}
