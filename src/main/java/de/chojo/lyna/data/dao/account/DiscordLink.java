package de.chojo.lyna.data.dao.account;

import java.time.Instant;

public record DiscordLink(
        int accountId,
        long discordUserId,
        Instant linkedAt,
        String verifiedVia
) {
    public enum Verification {
        OAUTH,
        BOT_DM_CODE;

        public String dbValue() {
            return name().toLowerCase();
        }
    }
}
