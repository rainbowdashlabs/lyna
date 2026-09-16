package de.chojo.lyna.service;

import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A purchase finding its buyer.
 *
 * <p>Somebody pays in the shop with one address and signs up with another. The shop knows only the
 * address it was paid from, so the licence it mints names that address and nobody holds it. Proving
 * the address is what connects the two - and it has to be proving, not merely claiming, because the
 * whole thing rests on it.
 */
class PurchaseCollectionServiceTest extends RepositoryTestBase {
    private static final long GUILD = 7001L;
    private int productId;

    @BeforeEach
    void seed() throws SQLException {
        clear("license_invite", "user_sub_license", "user_license", "license_access", "license",
                "product", "account_email", "account_identity", "account");
        try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (var rows = statement.executeQuery(
                    "INSERT INTO %s.product (guild_id, name, role) VALUES (%d, 'Chatty', 1) RETURNING id"
                            .formatted(schemaName, GUILD))) {
                rows.next();
                productId = rows.getInt(1);
            }
        }
    }

    /**
     * What the shop webhook leaves behind: a licence naming the address it was paid from, held by
     * nobody.
     */
    private int purchase(String payingAddress) throws SQLException {
        try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     INSERT INTO %s.license (product_id, user_identifier, key, source)
                     VALUES (%d, '%s', '%s', 'KOFI') RETURNING id
                     """.formatted(schemaName, productId, payingAddress, "KEY-" + payingAddress.hashCode()))) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test
    @DisplayName("Proving the address it was paid from hands the licence over")
    void provingThePayingAddressCollectsTheLicence() throws SQLException {
        int licenseId = purchase("paid-with@example.invalid");
        Account account = accounts.create("signed-up-with@example.invalid", "hash");

        List<Integer> collected = accounts.confirmEmail(account.id(), "paid-with@example.invalid");

        assertEquals(List.of(licenseId), collected);
        assertEquals(1, accountLicenses.owned(account.id()).size());
    }

    @Test
    @DisplayName("The address is matched however the shop capitalised it")
    void matchingIgnoresCase() throws SQLException {
        purchase("Mixed.Case@Example.invalid");
        Account account = accounts.create("signed-up@example.invalid", "hash");

        assertEquals(1, accounts.confirmEmail(account.id(), "mixed.case@example.invalid").size());
    }

    @Test
    @DisplayName("A licence somebody already holds stays theirs")
    void anAlreadyHeldLicenceIsNotTakenAway() throws SQLException {
        int licenseId = purchase("paid-with@example.invalid");
        Account holder = accounts.create("holder@example.invalid", "hash");
        try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO %s.user_license (account_id, license_id) VALUES (%d, %d)"
                    .formatted(schemaName, holder.id(), licenseId));
        }

        Account latecomer = accounts.create("latecomer@example.invalid", "hash");
        List<Integer> collected = accounts.confirmEmail(latecomer.id(), "paid-with@example.invalid");

        assertEquals(List.of(), collected);
        assertEquals(1, accountLicenses.owned(holder.id()).size());
        assertTrue(accountLicenses.owned(latecomer.id()).isEmpty());
    }

    @Test
    @DisplayName("A licence an operator made by hand is not collected by proving an address")
    void onlyPurchasesAreCollected() throws SQLException {
        try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO %s.license (product_id, user_identifier, key, source)
                    VALUES (%d, 'by-hand@example.invalid', 'KEY-MANUAL', 'MANUAL')
                    """.formatted(schemaName, productId));
        }
        Account account = accounts.create("somebody@example.invalid", "hash");

        assertEquals(List.of(), accounts.confirmEmail(account.id(), "by-hand@example.invalid"));
    }

    @Test
    @DisplayName("Claiming the address is not enough - it has to be proved")
    void claimingAloneCollectsNothing() throws SQLException {
        purchase("paid-with@example.invalid");
        Account account = accounts.create("signed-up@example.invalid", "hash");

        accountEmails.add(account.id(), "paid-with@example.invalid");

        assertTrue(accountLicenses.owned(account.id()).isEmpty());
    }
}
