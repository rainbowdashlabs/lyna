/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.entity;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * A kind of server key one product hands out, such as the key a server proves itself to that product's
 * hosted dashboard with.
 *
 * <p>Everything about such a key belongs to the product: what it is called, how a buyer hands it to a
 * server, how often it is replaced and when it expires, how many an account may hold, and the secret
 * its gateway checks keys with. That secret opens keys of this type and of no other, so a key bought
 * for one product never passes the gateway of another.
 *
 * @param slug           how the type is named to a gateway, unique within its product
 * @param name           what buyers see it called
 * @param command        how a key is handed to a server, with {@value #KEY_SLOT} where the key goes; null
 *                       shows the bare key
 * @param enabled        whether accounts may hold and use keys of the type
 * @param refreshDays    how old a key may get before a refresh trades it for its successor
 * @param expiryDays     how long a key may go unused before it expires
 * @param allowance      how many keys a buyer may hold before asking for more
 * @param trialAllowance how many keys an account that only took the product's trial may hold
 * @param gatewaySecret  what the type's gateway authenticates with
 */
public record ProductKeyType(
        int id,
        int productId,
        String slug,
        String name,
        @Nullable String command,
        boolean enabled,
        int refreshDays,
        int expiryDays,
        int allowance,
        int trialAllowance,
        String gatewaySecret) {
    /** Where the key goes in a {@link #command()}. */
    public static final String KEY_SLOT = "{key}";

    /**
     * What an admin sets on a key type; the id, product and secret are Lyna's.
     */
    public record Draft(
            String slug,
            String name,
            @Nullable String command,
            boolean enabled,
            int refreshDays,
            int expiryDays,
            int allowance,
            int trialAllowance) {
        /** The most days either interval may be set to. */
        public static final int MAX_DAYS = 3650;
        /** The most keys an account may be allowed by default. */
        public static final int MAX_ALLOWANCE = 1000;

        public static final int MAX_NAME_LENGTH = 64;
        public static final int MAX_COMMAND_LENGTH = 200;
        private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");

        /**
         * @return whether every field is within what may be stored
         */
        public boolean valid() {
            return slug != null
                    && SLUG.matcher(slug).matches()
                    && name != null
                    && !name.isBlank()
                    && name.strip().length() <= MAX_NAME_LENGTH
                    && (command == null || command.strip().length() <= MAX_COMMAND_LENGTH)
                    && refreshDays >= 1
                    && refreshDays <= MAX_DAYS
                    && expiryDays >= 1
                    && expiryDays <= MAX_DAYS
                    && allowance >= 0
                    && allowance <= MAX_ALLOWANCE
                    && trialAllowance >= 0
                    && trialAllowance <= MAX_ALLOWANCE;
        }

        /**
         * @return the draft with its texts trimmed and an empty command as none
         */
        public Draft cleaned() {
            String trimmedCommand = command == null || command.isBlank() ? null : command.strip();
            return new Draft(
                    slug, name.strip(), trimmedCommand, enabled, refreshDays, expiryDays, allowance, trialAllowance);
        }
    }
}
