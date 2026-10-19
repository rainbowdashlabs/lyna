/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.elements.Auth;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The 256-bit key stored Discord tokens are encrypted with.
 *
 * <p>Found in this order: {@code auth.tokenKey} (or {@code AUTH_TOKENKEY}), base64, for deployments
 * that keep their secrets out of files; then {@value #FILE} beside the configuration; and when neither
 * exists, a new key is made and written there, readable by its owner alone. The key lives beside the
 * configuration rather than in the database, so a dump of the database decrypts nothing. It is not
 * written through the configuration, which would also write every value the environment supplied.
 *
 * <p>A key that is there but cannot be read stops start-up. Making a new one over it would silently
 * lose every stored token; losing the key on purpose costs only those tokens, which are refilled at
 * the next Discord sign-in.
 */
@Singleton
public class TokenKey {
    public static final String FILE = "token.key";
    private static final Logger log = getLogger(TokenKey.class);
    private static final int BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String configured;
    private final Path file;
    private SecretKey key;

    @Inject
    public TokenKey(Auth auth, Conf conf) {
        this(auth.tokenKey(), conf.base().resolve(FILE));
    }

    /**
     * @param configured the key from the configuration, base64, or blank to use the file
     * @param file       where the key is kept otherwise
     */
    public TokenKey(String configured, Path file) {
        this.configured = configured;
        this.file = file;
    }

    /**
     * Finds the key now rather than at its first use, so that one which cannot be read stops the
     * application while it is starting.
     *
     * @throws IllegalStateException when the key is there but unreadable, or cannot be written
     */
    public void load() {
        key();
    }

    /**
     * @return the key, found or made on first use
     */
    public synchronized SecretKey key() {
        if (key == null) key = resolve();
        return key;
    }

    private SecretKey resolve() {
        if (configured != null && !configured.isBlank()) return decode(configured, "auth.tokenKey");
        if (Files.exists(file)) return decode(read(), file.toString());
        return generate();
    }

    private String read() {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the token key at " + file, e);
        }
    }

    private static SecretKey decode(String encoded, String source) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The token key in %s is not base64".formatted(source), e);
        }
        if (raw.length != BYTES) {
            throw new IllegalStateException(
                    "The token key in %s is %d bytes long, not %d".formatted(source, raw.length, BYTES));
        }
        return new SecretKeySpec(raw, "AES");
    }

    private SecretKey generate() {
        byte[] raw = new byte[BYTES];
        RANDOM.nextBytes(raw);
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.createFile(
                        file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } else {
                Files.createFile(file);
            }
            Files.writeString(file, Base64.getEncoder().encodeToString(raw), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write a new token key to " + file, e);
        }
        log.info("Made a new key for the stored Discord tokens at {}", file.toAbsolutePath());
        return new SecretKeySpec(raw, "AES");
    }
}
