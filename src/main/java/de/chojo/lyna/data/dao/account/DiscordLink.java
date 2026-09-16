package de.chojo.lyna.data.dao.account;

import java.time.Instant;

/**
 * An account's Discord identity.
 *
 * @param handle what that id is called, as Discord last told us. Kept here because turning an id
 *               into a name otherwise means asking the gateway, and the web deliberately does not
 *               depend on it - so a page that has only this row can still name somebody.
 */
public record DiscordLink(
        int accountId,
        long discordUserId,
        Instant linkedAt,
        String verifiedVia,
        String handle
) {
    /**
     * @return the handle if one was ever recorded, otherwise the id, which is what the pages showed
     * before there was anywhere to keep a handle
     */
    public String display() {
        return handle == null || handle.isBlank() ? Long.toString(discordUserId) : handle;
    }

    public enum Verification {
        OAUTH,
        BOT_DM_CODE;

        public String dbValue() {
            return name().toLowerCase();
        }
    }
}
