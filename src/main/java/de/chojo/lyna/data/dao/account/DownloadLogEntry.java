package de.chojo.lyna.data.dao.account;

import java.time.Instant;

public record DownloadLogEntry(
        long id,
        Integer accountId,
        Long discordId,
        Integer licenseId,
        int productId,
        String productName,
        int downloadId,
        String version,
        String source,
        Instant downloadedAt
) {
}
