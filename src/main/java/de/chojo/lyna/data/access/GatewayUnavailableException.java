package de.chojo.lyna.data.access;

/**
 * Thrown where something can only be answered with the Discord gateway connected.
 *
 * <p>A product's download types and its Nexus client hang off the guild that owns it, which is
 * reached through the bot. An instance running with the bot switched off still serves the
 * storefront, the account area and the API, and this is what tells a caller that the part they
 * asked for is not one of them - rather than failing as though something had broken.
 */
public class GatewayUnavailableException extends RuntimeException {
    public GatewayUnavailableException(String message) {
        super(message);
    }
}
