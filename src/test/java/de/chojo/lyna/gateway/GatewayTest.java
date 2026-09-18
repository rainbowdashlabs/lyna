package de.chojo.lyna.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An instance running without a bot.
 *
 * <p>The whole reason this is an interface: a deployment with {@code botEnabled} off has no gateway,
 * and that has to be an answer rather than a null somebody forgot to check. Every method returns
 * nothing, and none of them throws.
 */
class GatewayTest {

    @Test
    @DisplayName("Without a bot there is no gateway, and it says so")
    void noneIsNotConnected() {
        assertFalse(Gateway.NONE.connected());
    }

    @Test
    @DisplayName("Every question has an empty answer rather than an exception")
    void noneAnswersEmpty() {
        assertTrue(Gateway.NONE.guild(1234L).isEmpty());
        assertTrue(Gateway.NONE.guilds().isEmpty());
        assertTrue(Gateway.NONE.member(1234L, 5678L).isEmpty());
    }

    @Test
    @DisplayName("A gateway whose bot never connected behaves like none at all")
    void anUnconnectedShardManagerIsAsGoodAsNone() {
        Gateway gateway = new JdaGateway(() -> null);

        assertFalse(gateway.connected());
        assertTrue(gateway.guild(1234L).isEmpty());
        assertTrue(gateway.guilds().isEmpty());
        assertTrue(gateway.member(1234L, 5678L).isEmpty());
    }
}
