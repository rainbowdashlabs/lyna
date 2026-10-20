/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The roles an account wears: Discord's are replaced by every sync, anything else is left alone.
 */
class AccountRoleRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 4801L;
    private static final long OTHER_GUILD = 4802L;

    private int account;

    @BeforeEach
    void seed() throws SQLException {
        clear("account_role", "account");
        account = accounts.insert(null).id();
    }

    private void manual(long roleId) throws SQLException {
        execute("INSERT INTO %s.account_role (account_id, guild_id, role_id, source) VALUES (%d, %d, %d, 'MANUAL')"
                .formatted(schemaName, account, GUILD, roleId));
    }

    @Test
    @DisplayName("A sync makes the guild's Discord roles exactly what it was given")
    void replaceIsExact() {
        accountRoles.replaceDiscordRoles(account, GUILD, List.of(1L, 2L));
        accountRoles.replaceDiscordRoles(account, GUILD, List.of(2L, 3L));

        assertEquals(List.of(2L, 3L), accountRoles.roles(account));
    }

    @Test
    @DisplayName("A sync of one guild leaves the other guild and other sources alone")
    void replaceIsScoped() throws SQLException {
        manual(9L);
        accountRoles.replaceDiscordRoles(account, OTHER_GUILD, List.of(5L));
        accountRoles.replaceDiscordRoles(account, GUILD, List.of(1L));

        accountRoles.replaceDiscordRoles(account, GUILD, List.of());

        assertEquals(List.of(5L, 9L), accountRoles.roles(account));
    }

    @Test
    @DisplayName("Leaving a guild clears its Discord roles, unlinking clears all of them")
    void clearing() throws SQLException {
        manual(9L);
        accountRoles.replaceDiscordRoles(account, GUILD, List.of(1L));
        accountRoles.replaceDiscordRoles(account, OTHER_GUILD, List.of(5L));

        accountRoles.clearDiscordRoles(account, GUILD);
        assertEquals(List.of(5L, 9L), accountRoles.roles(account));

        accountRoles.clearDiscordRoles(account);
        assertEquals(List.of(9L), accountRoles.roles(account));
    }

    @Test
    @DisplayName("A deleted role is gone whoever gave it")
    void removeRole() throws SQLException {
        manual(1L);
        accountRoles.replaceDiscordRoles(account, GUILD, List.of(1L, 2L));

        accountRoles.removeRole(1L);

        assertEquals(List.of(2L), accountRoles.roles(account));
    }
}
