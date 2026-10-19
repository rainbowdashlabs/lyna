/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts the tokens kept for somebody, with AES-GCM under the {@link TokenKey}.
 *
 * <p>Each value carries its own random nonce in front of the ciphertext. A context - which account a
 * value belongs to - is bound in as associated data, so a value moved to another row no longer opens.
 */
@Singleton
public class TokenCipher {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TokenKey key;

    @Inject
    public TokenCipher(TokenKey key) {
        this.key = key;
    }

    /**
     * @param context what the value belongs to; the same context is needed to open it again
     * @return the nonce followed by the ciphertext and its tag
     */
    public byte[] seal(String plaintext, String context) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, context);
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(NONCE_BYTES + sealed.length)
                    .put(nonce)
                    .put(sealed)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM is not available", e);
        }
    }

    /**
     * @throws Unreadable when the value was sealed under another key, for another context, or has
     *     been altered
     */
    public String open(byte[] sealed, String context) throws Unreadable {
        if (sealed == null || sealed.length <= NONCE_BYTES) throw new Unreadable(null);
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(sealed, NONCE_BYTES), context);
            byte[] plain = cipher.doFinal(sealed, NONCE_BYTES, sealed.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new Unreadable(e);
        }
    }

    private Cipher cipher(int mode, byte[] nonce, String context) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, key.key(), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    /**
     * A value this key cannot open.
     */
    public static class Unreadable extends Exception {
        public Unreadable(Throwable cause) {
            super("The value cannot be decrypted with this key", cause);
        }
    }
}
