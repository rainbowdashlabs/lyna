/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.entity;

import java.time.Instant;

/**
 * An account as some other service knows it.
 *
 * <p>Discord is one such service rather than the only one, which is why nothing here is named after
 * it: a second provider is a new value of {@link #provider}, not a new table.
 *
 * @param externalId what the provider calls this account, as text. Providers disagree about what an
 *                   id is - Discord issues snowflakes, others issue opaque strings - so the widest
 *                   shape is stored and the narrowing happens at the edge that needs it.
 * @param handle     what the provider says this id is called. Kept because turning an id into a name
 *                   otherwise means asking the provider, and the web deliberately does not depend on
 *                   being able to - so a page holding only this row can still name somebody.
 */
public record AccountIdentity(
        int accountId, String provider, String externalId, Instant linkedAt, String verifiedVia, String handle) {
    public static final String DISCORD = "discord";

    /**
     * @return the id as the snowflake it is, for the half of the application that talks to Discord
     * @throws NumberFormatException if asked of a provider whose ids are not numeric
     */
    public long externalIdAsLong() {
        return Long.parseLong(externalId);
    }

    /**
     * @return the handle if one was ever recorded, otherwise the raw id, which is what the pages
     * showed before there was anywhere to keep a handle
     */
    public String display() {
        return handle == null || handle.isBlank() ? externalId : handle;
    }

    /**
     * How we came to believe this identity belongs to this account.
     */
    public enum Verification {
        OAUTH,
        BOT_DM_CODE;

        public String dbValue() {
            return name().toLowerCase();
        }
    }
}
