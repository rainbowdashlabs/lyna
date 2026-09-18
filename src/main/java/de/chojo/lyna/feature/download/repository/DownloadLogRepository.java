/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.repository;

import de.chojo.lyna.feature.download.entity.DownloadLogEntry;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class DownloadLogRepository {
    private static final String SELECT_ENTRY = """
            SELECT dl.id, dl.account_id, dl.discord_id, dl.license_id,
                   dl.product_id, p.name AS product_name,
                   dl.download_id, dl.version, dl.source, dl.downloaded_at
            FROM download_log dl
            LEFT JOIN product p ON p.id = dl.product_id
            """;

    /**
     * Every filter is optional and written the same way: null means "do not narrow by this".
     * Writing them into one clause keeps the paged query and the count that pages it in step, which
     * is what stops a page control from promising rows the query will not return.
     */
    private static final String FILTERS = """
            WHERE (?::INTEGER IS NULL OR dl.account_id = ?::INTEGER)
              AND (?::INTEGER IS NULL OR dl.license_id = ?::INTEGER)
              AND (?::INTEGER IS NULL OR dl.product_id = ?::INTEGER)
              AND (?::TEXT IS NULL OR dl.source = ?::TEXT)
              AND (?::TIMESTAMP IS NULL OR dl.downloaded_at >= ?::TIMESTAMP)
              AND (?::TIMESTAMP IS NULL OR dl.downloaded_at <= ?::TIMESTAMP)
            """;

    public void record(
            Integer accountId,
            Long discordId,
            Integer licenseId,
            int productId,
            int downloadId,
            String version,
            String source,
            String userAgent,
            String ipHash) {
        query("""
                INSERT INTO download_log
                    (account_id, discord_id, license_id, product_id, download_id, version, source, user_agent, ip_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .single(call().bind(accountId)
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
                .map(DownloadLogRepository::readEntry)
                .all();
    }

    /**
     * What has been downloaded on one license.
     *
     * @param licenseId the license
     * @param accountId when given, only that account's own downloads - which is what a sharee may
     *                  see; the owner passes null and sees every holder's
     * @param limit     how many rows at most
     * @return the downloads, newest first
     */
    public List<DownloadLogEntry> recentForLicense(int licenseId, Integer accountId, int limit) {
        return query("""
                SELECT dl.id, dl.account_id, dl.discord_id, dl.license_id,
                       dl.product_id, p.name AS product_name,
                       dl.download_id, dl.version, dl.source, dl.downloaded_at
                FROM download_log dl
                LEFT JOIN product p ON p.id = dl.product_id
                WHERE dl.license_id = ?
                  AND (?::INTEGER IS NULL OR dl.account_id = ?::INTEGER)
                ORDER BY dl.downloaded_at DESC
                LIMIT ?
                """)
                .single(call().bind(licenseId).bind(accountId).bind(accountId).bind(limit))
                .map(DownloadLogRepository::readEntry)
                .all();
    }

    /**
     * One page of an account's downloads, narrowed by whatever the filters name.
     *
     * <p>A license filter is honoured only when the caller owns the license; the endpoint decides
     * that and passes the id on, or leaves it out. Without one the rows are the account's own.
     *
     * @param accountId the account asking, or null when a license is being read on its behalf
     * @param licenseId a license to narrow to, or null
     * @param productId a product to narrow to, or null
     * @param source    an entitlement path to narrow to, or null
     * @param from      the earliest moment to include, or null
     * @param to        the latest moment to include, or null
     * @param limit     rows per page
     * @param offset    rows to skip
     * @return the page, newest first
     */
    public List<DownloadLogEntry> page(
            Integer accountId,
            Integer licenseId,
            Integer productId,
            String source,
            Instant from,
            Instant to,
            int limit,
            int offset) {
        return query(SELECT_ENTRY + FILTERS + """
                ORDER BY dl.downloaded_at DESC
                LIMIT ? OFFSET ?
                """)
                .single(bindFilters(accountId, licenseId, productId, source, from, to)
                        .bind(limit)
                        .bind(offset))
                .map(DownloadLogRepository::readEntry)
                .all();
    }

    /**
     * @return how many rows the same filters match, which is what the page control counts
     */
    public int count(Integer accountId, Integer licenseId, Integer productId, String source, Instant from, Instant to) {
        return query("SELECT count(*) AS total FROM download_log dl\n" + FILTERS)
                .single(bindFilters(accountId, licenseId, productId, source, from, to))
                .map(row -> row.getInt("total"))
                .first()
                .orElse(0);
    }

    /**
     * @return the products the account has ever downloaded, which is what its product filter offers
     */
    public List<ProductOption> productsForAccount(int accountId) {
        return query("""
                SELECT DISTINCT dl.product_id, p.name AS product_name
                FROM download_log dl
                LEFT JOIN product p ON p.id = dl.product_id
                WHERE dl.account_id = ?
                ORDER BY product_name
                """)
                .single(call().bind(accountId))
                .map(row -> new ProductOption(row.getInt("product_id"), row.getString("product_name")))
                .all();
    }

    private static de.chojo.sadu.queries.api.call.Call bindFilters(
            Integer accountId, Integer licenseId, Integer productId, String source, Instant from, Instant to) {
        return call().bind(accountId)
                .bind(accountId)
                .bind(licenseId)
                .bind(licenseId)
                .bind(productId)
                .bind(productId)
                .bind(source)
                .bind(source)
                .bind(from == null ? null : Timestamp.from(from))
                .bind(from == null ? null : Timestamp.from(from))
                .bind(to == null ? null : Timestamp.from(to))
                .bind(to == null ? null : Timestamp.from(to));
    }

    public record ProductOption(int id, String name) {}

    private static DownloadLogEntry readEntry(Row row) throws SQLException {
        return new DownloadLogEntry(
                row.getLong("id"),
                (Integer) row.getObject("account_id"),
                (Long) row.getObject("discord_id"),
                (Integer) row.getObject("license_id"),
                row.getInt("product_id"),
                row.getString("product_name"),
                row.getInt("download_id"),
                row.getString("version"),
                row.getString("source"),
                toInstant(row.getTimestamp("downloaded_at")));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
