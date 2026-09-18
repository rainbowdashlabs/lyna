package de.chojo.lyna.auth;

import de.chojo.lyna.configuration.elements.Auth;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private JwtService service(long expirySeconds) {
        Auth config = Mockito.mock(Auth.class);
        Mockito.when(config.jwtSecret()).thenReturn("test-secret-please-rotate");
        Mockito.when(config.jwtExpirySeconds()).thenReturn(expirySeconds);
        return new JwtService(config);
    }

    @Test
    void issueAndVerify() {
        JwtService svc = service(3600);
        JwtService.Issued issued = svc.issue(42, 100200300L);
        assertNotNull(issued.jti());

        Optional<JwtService.Verified> verified = svc.verify(issued.token());
        assertTrue(verified.isPresent());
        assertEquals(42, verified.get().accountId());
        assertEquals(100200300L, verified.get().discordId());
        assertEquals(issued.jti(), verified.get().jti());
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtService svc = service(1);
        JwtService.Issued issued = svc.issue(7, null);
        // The java-jwt verifier allows a small leeway; sleep past the expiry plus its tolerance.
        Thread.sleep(2_000);
        assertFalse(svc.verify(issued.token()).isPresent());
    }

    @Test
    void rejectsTamperedSignature() {
        JwtService svc = service(3600);
        String token = svc.issue(1, null).token();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertFalse(svc.verify(tampered).isPresent());
    }
}
