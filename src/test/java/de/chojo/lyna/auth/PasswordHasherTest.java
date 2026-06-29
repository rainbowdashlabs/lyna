package de.chojo.lyna.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void verifiesCorrectPassword() {
        String hash = hasher.hash("hunter2");
        assertTrue(hasher.verify("hunter2", hash));
    }

    @Test
    void rejectsWrongPassword() {
        String hash = hasher.hash("hunter2");
        assertFalse(hasher.verify("hunter3", hash));
    }

    @Test
    void hashesAreSalted() {
        String a = hasher.hash("same");
        String b = hasher.hash("same");
        assertNotEquals(a, b);
    }
}
