/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.auth;

import de.chojo.lyna.configuration.Conf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the key for the stored Discord tokens comes from, and what happens when it is not there or
 * cannot be read.
 */
class TokenKeyTest {
    private static final byte[] RAW = new byte[32];

    static {
        for (int i = 0; i < RAW.length; i++) RAW[i] = (byte) i;
    }

    private static String encoded() {
        return Base64.getEncoder().encodeToString(RAW);
    }

    @Test
    @DisplayName("A configured key is used as it is, and nothing is written")
    void configuredKeyWins(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(TokenKey.FILE);
        Files.writeString(file, Base64.getEncoder().encodeToString(new byte[32]));

        TokenKey key = new TokenKey(" " + encoded() + "\n", file);

        assertArrayEquals(RAW, key.key().getEncoded());
        assertEquals("AES", key.key().getAlgorithm());
    }

    @Test
    @DisplayName("Without a configured key the file beside the configuration is read")
    void fileIsRead(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(TokenKey.FILE);
        Files.writeString(file, encoded() + "\n");

        assertArrayEquals(RAW, new TokenKey("", file).key().getEncoded());
    }

    @Test
    @DisplayName("Without either, a key is made, written readable by its owner alone, and found again")
    void missingKeyIsGenerated(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("nested").resolve(TokenKey.FILE);

        TokenKey first = new TokenKey(null, file);
        first.load();

        assertTrue(Files.exists(file));
        assertEquals(32, Base64.getDecoder().decode(Files.readString(file).trim()).length);
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        assertArrayEquals(
                first.key().getEncoded(), new TokenKey(null, file).key().getEncoded());
        assertSame(first.key(), first.key(), "found once and held");
    }

    @Test
    @DisplayName("A key file that is not base64 stops start-up rather than being replaced")
    void corruptFileFails(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(TokenKey.FILE);
        Files.writeString(file, "this is not base64 ***");

        assertThrows(IllegalStateException.class, () -> new TokenKey("", file).load());
        assertEquals("this is not base64 ***", Files.readString(file), "the file is left as it was");
    }

    @Test
    @DisplayName("A key of the wrong length stops start-up, configured or in the file")
    void wrongLengthFails(@TempDir Path directory) throws IOException {
        String short16 = Base64.getEncoder().encodeToString(new byte[16]);
        Path file = directory.resolve(TokenKey.FILE);
        Files.writeString(file, short16);

        assertThrows(IllegalStateException.class, () -> new TokenKey(short16, directory.resolve("other")).load());
        assertThrows(IllegalStateException.class, () -> new TokenKey("", file).load());
    }

    @Test
    @DisplayName("A key that cannot be written stops start-up")
    void unwritableLocationFails(@TempDir Path directory) throws IOException {
        Path blocker = directory.resolve("a-file");
        Files.writeString(blocker, "");

        assertThrows(IllegalStateException.class, () -> new TokenKey("", blocker.resolve(TokenKey.FILE)).load());
    }

    @Test
    @DisplayName("A key file that cannot be read stops start-up")
    void unreadableFileFails(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(TokenKey.FILE);
        Files.createDirectory(file);

        assertThrows(IllegalStateException.class, () -> new TokenKey("", file).load());
    }

    @Test
    @DisplayName("The application keeps the key in its configuration directory")
    void keptBesideTheConfiguration(@TempDir Path directory) {
        Conf conf = new Conf(directory);

        new TokenKey(conf.main().auth(), conf).load();

        assertTrue(Files.exists(directory.resolve(TokenKey.FILE)));
    }
}
