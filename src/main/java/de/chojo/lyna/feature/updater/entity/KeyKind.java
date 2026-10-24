/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.entity;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * What a server key is for. Both kinds live in the same tables and follow the same rules of
 * ownership, allowance and requests; they differ in what they open and in how they are rotated.
 *
 * <p>Each kind has a prefix of its own, so a leaked key says what it is, and a key presented where the
 * other kind is expected is refused before anything is looked up.
 */
public enum KeyKind {
    /** An upgrade token, traded by an updater for a download and replaced on every trade. */
    UPDATE("lyu_"),
    /**
     * A key of one of a product's {@link ProductKeyType key types}, with which a server proves itself to
     * that product's gateway; replaced on refresh. The prefix is the one dashboard keys carried, so keys
     * issued before key types existed stay valid.
     */
    SERVER("lyd_");

    private final String prefix;

    KeyKind(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    /**
     * @return whether the key carries this kind's prefix
     */
    public boolean marks(@Nullable String key) {
        return key != null && key.startsWith(prefix);
    }

    /**
     * Reads a kind as a client names it. Nothing named means upgrade tokens, which is what every
     * client meant before there was a second kind.
     *
     * @return the kind, or empty for a name that is none
     */
    public static Optional<KeyKind> parse(@Nullable String name) {
        if (name == null || name.isBlank()) return Optional.of(UPDATE);
        try {
            return Optional.of(valueOf(name.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
