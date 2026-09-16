package de.chojo.lyna.repository;

import de.chojo.lyna.data.access.DownloadLog;
import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.DownloadLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadLogRepositoryTest extends RepositoryTestBase {
    private static final long GUILD_ID = 4711L;

    private Account account;
    private int productId;
    private int downloadId;

    @BeforeEach
    void seedCatalog() throws SQLException {
        clear("download_log", "download", "download_type", "product", "account_identity", "account");
        account = accounts.create("downloads@example.invalid", "hash");

        try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            productId = insert(statement, """
                    INSERT INTO product (guild_id, name, role) VALUES (%d, 'Chatty', 1) RETURNING id
                    """.formatted(GUILD_ID));
            int typeId = insert(statement, """
                    INSERT INTO download_type (guild_id, name, description, release_type)
                    VALUES (%d, 'Jar', 'Plain jar', 'STABLE') RETURNING id
                    """.formatted(GUILD_ID));
            downloadId = insert(statement, """
                    INSERT INTO download (product_id, type_id, repository, group_id, artifact_id)
                    VALUES (%d, %d, 'releases', 'de.chojo', 'chatty') RETURNING id
                    """.formatted(productId, typeId));
        }
    }

    private static int insert(Statement statement, String sql) throws SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test
    @DisplayName("A recorded download comes back with the product it was for")
    void recordAndRead() {
        downloadLog.record(account.id(), 99L, null, productId, downloadId, "1.0.0", "free", "curl/8", null);

        DownloadLogEntry entry = downloadLog.recentForAccount(account.id(), 25).getFirst();
        assertEquals(account.id(), entry.accountId());
        assertEquals(99L, entry.discordId());
        assertEquals(productId, entry.productId());
        assertEquals("Chatty", entry.productName());
        assertEquals("1.0.0", entry.version());
        assertEquals("free", entry.source());
        assertNull(entry.licenseId());
    }

    @Test
    @DisplayName("An anonymous download is kept, and belongs to no account")
    void anonymousDownloadIsRecorded() throws SQLException {
        downloadLog.record(null, null, null, productId, downloadId, "1.0.0", "free", null, "hash");

        assertTrue(downloadLog.recentForAccount(account.id(), 25).isEmpty());
        assertEquals(1, countRows("download_log"));
    }

    @Test
    @DisplayName("The newest download is listed first, and the limit is honoured")
    void newestFirstAndLimited() {
        for (int i = 1; i <= 5; i++) {
            downloadLog.record(account.id(), null, null, productId, downloadId, "1.0." + i, "free", null, null);
        }

        List<String> versions = downloadLog.recentForAccount(account.id(), 3).stream()
                .map(DownloadLogEntry::version)
                .toList();

        assertEquals(3, versions.size());
        assertEquals("1.0.5", versions.getFirst());
    }

    @Test
    @DisplayName("Only the account's own downloads are listed")
    void scopedToTheAccount() {
        Account other = accounts.create("other-downloads@example.invalid", "hash");
        downloadLog.record(account.id(), null, null, productId, downloadId, "mine", "free", null, null);
        downloadLog.record(other.id(), null, null, productId, downloadId, "theirs", "free", null, null);

        assertEquals(List.of("mine"), downloadLog.recentForAccount(account.id(), 25).stream()
                .map(DownloadLogEntry::version).toList());
    }

    @Test
    @DisplayName("Deleting an account keeps its download rows, detached from it")
    void deletingAccountKeepsTheRow() throws SQLException {
        downloadLog.record(account.id(), 99L, null, productId, downloadId, "1.0.0", "free", null, null);

        accounts.delete(account.id());

        assertEquals(1, countRows("download_log"));
    }

    @Test
    @DisplayName("Paging hands out one page at a time and counts the whole match")
    void pagingCountsTheWholeMatch() {
        for (int i = 1; i <= 7; i++) {
            downloadLog.record(account.id(), null, null, productId, downloadId, "1.0." + i, "free", null, null);
        }

        assertEquals(3, downloadLog.page(account.id(), null, null, null, null, null, 3, 0).size());
        assertEquals(1, downloadLog.page(account.id(), null, null, null, null, null, 3, 6).size());
        assertEquals(7, downloadLog.count(account.id(), null, null, null, null, null));
    }

    @Test
    @DisplayName("The source filter narrows to one entitlement path")
    void sourceFilter() {
        downloadLog.record(account.id(), null, null, productId, downloadId, "free-one", "free", null, null);
        downloadLog.record(account.id(), null, null, productId, downloadId, "paid-one", "license", null, null);

        List<DownloadLogEntry> paid = downloadLog.page(account.id(), null, null, "license", null, null, 25, 0);

        assertEquals(List.of("paid-one"), paid.stream().map(DownloadLogEntry::version).toList());
        assertEquals(1, downloadLog.count(account.id(), null, null, "license", null, null));
    }

    @Test
    @DisplayName("The product filter narrows to one product")
    void productFilter() {
        downloadLog.record(account.id(), null, null, productId, downloadId, "1.0.0", "free", null, null);

        assertEquals(1, downloadLog.count(account.id(), null, productId, null, null, null));
        assertEquals(0, downloadLog.count(account.id(), null, productId + 999, null, null, null));
    }

    @Test
    @DisplayName("The date range leaves out what falls outside it")
    void dateRangeFilter() {
        downloadLog.record(account.id(), null, null, productId, downloadId, "1.0.0", "free", null, null);

        Instant now = Instant.now();
        assertEquals(1, downloadLog.count(account.id(), null, null, null,
                now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1))));
        assertEquals(0, downloadLog.count(account.id(), null, null, null,
                now.plus(Duration.ofDays(1)), null));
        assertEquals(0, downloadLog.count(account.id(), null, null, null,
                null, now.minus(Duration.ofDays(1))));
    }

    @Test
    @DisplayName("No filters at all means every row, which is what an owner reading a license gets")
    void noAccountFilterMeansEveryRow() {
        Account other = accounts.create("everyone@example.invalid", "hash");
        downloadLog.record(account.id(), null, null, productId, downloadId, "mine", "free", null, null);
        downloadLog.record(other.id(), null, null, productId, downloadId, "theirs", "free", null, null);

        assertEquals(2, downloadLog.count(null, null, null, null, null, null));
        assertEquals(1, downloadLog.count(account.id(), null, null, null, null, null));
    }

    @Test
    @DisplayName("The product filter offers only what the account has actually downloaded")
    void productOptionsFollowTheHistory() {
        assertTrue(downloadLog.productsForAccount(account.id()).isEmpty());

        downloadLog.record(account.id(), null, null, productId, downloadId, "1.0.0", "free", null, null);
        downloadLog.record(account.id(), null, null, productId, downloadId, "1.0.1", "free", null, null);

        List<DownloadLog.ProductOption> options = downloadLog.productsForAccount(account.id());
        assertEquals(1, options.size());
        assertEquals("Chatty", options.getFirst().name());
    }
}
