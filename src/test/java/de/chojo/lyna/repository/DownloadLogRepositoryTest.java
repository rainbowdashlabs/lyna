package de.chojo.lyna.repository;

import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.DownloadLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
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
        clear("download_log", "download", "download_type", "product", "account_discord_link", "account");
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

    private static int countRows(String table) throws SQLException {
        try (var connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM %s.%s".formatted(schemaName, table))) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
