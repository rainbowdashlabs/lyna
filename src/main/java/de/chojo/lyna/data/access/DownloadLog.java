package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.account.DownloadLogEntry;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class DownloadLog {

    public void record(Integer accountId, Long discordId, Integer licenseId, int productId,
                       int downloadId, String version, String source, String userAgent, String ipHash) {
        query("""
                INSERT INTO download_log
                    (account_id, discord_id, license_id, product_id, download_id, version, source, user_agent, ip_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .single(call()
                        .bind(accountId)
                        .bind(discordId)
                        .bind(licenseId)
                        .bind(productId)
                        .bind(downloadId)
                        .bind(version)
                        .bind(source)
                        .bind(userAgent)
                        .bind(ipHash))
                .insert();
    }

    public List<DownloadLogEntry> recentForAccount(int accountId, int limit) {
        return query("""
                SELECT dl.id, dl.account_id, dl.discord_id, dl.license_id,
                       dl.product_id, p.name AS product_name,
                       dl.download_id, dl.version, dl.source, dl.downloaded_at
                FROM download_log dl
                LEFT JOIN product p ON p.id = dl.product_id
                WHERE dl.account_id = ?
                ORDER BY dl.downloaded_at DESC
                LIMIT ?
                """)
                .single(call().bind(accountId).bind(limit))
                .map(row -> new DownloadLogEntry(
                        row.getLong("id"),
                        (Integer) row.getObject("account_id"),
                        (Long) row.getObject("discord_id"),
                        (Integer) row.getObject("license_id"),
                        row.getInt("product_id"),
                        row.getString("product_name"),
                        row.getInt("download_id"),
                        row.getString("version"),
                        row.getString("source"),
                        toInstant(row.getTimestamp("downloaded_at"))))
                .all();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
