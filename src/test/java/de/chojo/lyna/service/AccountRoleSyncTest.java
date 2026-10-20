/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.service.AccountRoleSync;
import de.chojo.lyna.gateway.Gateway;
import de.chojo.lyna.repository.RepositoryTestBase;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.utils.concurrent.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stored roles follow Discord: on sign-in, on a full sync, and on the events the gateway sends. A guild
 * the gateway cannot see, or a Discord failure, leaves the rows as they were.
 */
class AccountRoleSyncTest extends RepositoryTestBase {
    private static final long GUILD = 4901L;
    private static final long DISCORD = 7201L;
    private static final long OTHER_DISCORD = 7202L;

    private final Gateway gateway = mock(Gateway.class);
    private final Guild guild = mock(Guild.class);
    private AccountRoleSync sync;
    private int account;
    private int other;

    @BeforeEach
    void seed() throws SQLException {
        clear("account_role", "product", "account_identity", "account");
        execute("INSERT INTO %s.product (guild_id, name, role) VALUES (%d, 'Mirrored', 5)"
                .formatted(schemaName, GUILD));
        sync = new AccountRoleSync(accountRoles, accounts, productRepository, gateway);
        account = accounts.insert(null).id();
        other = accounts.insert(null).id();
        accountLinks.link(account, DISCORD, AccountIdentity.Verification.OAUTH);
        accountLinks.link(other, OTHER_DISCORD, AccountIdentity.Verification.OAUTH);
        when(guild.getIdLong()).thenReturn(GUILD);
        when(gateway.guild(GUILD)).thenReturn(Optional.of(guild));
        when(gateway.member(Mockito.anyLong(), Mockito.anyLong())).thenReturn(Optional.empty());
    }

    private static Member member(long id, long... roleIds) {
        Member member = mock(Member.class);
        when(member.getIdLong()).thenReturn(id);
        List<Role> roles = Arrays.stream(roleIds)
                .mapToObj(roleId -> {
                    Role role = mock(Role.class);
                    when(role.getIdLong()).thenReturn(roleId);
                    return role;
                })
                .toList();
        when(member.getRoles()).thenReturn(roles);
        return member;
    }

    @Test
    @DisplayName("Signing in takes the roles the member wears now")
    void signInTakesCurrentRoles() {
        accountRoles.replaceDiscordRoles(account, GUILD, List.of(1L));
        Member wearing = member(DISCORD, 2L, 3L);
        when(gateway.member(GUILD, DISCORD)).thenReturn(Optional.of(wearing));

        sync.syncAccount(account);

        assertEquals(List.of(2L, 3L), accountRoles.roles(account));
    }

    @Test
    @DisplayName("Signing in as somebody no longer in the guild clears their roles there")
    void signInOutsideTheGuildClears() {
        accountRoles.replaceDiscordRoles(account, GUILD, List.of(1L));

        sync.syncAccount(account);

        assertEquals(List.of(), accountRoles.roles(account));
    }

    @Test
    @DisplayName("A guild the gateway cannot see, or a failing Discord, leaves the rows alone")
    void unreachableLeavesRows() {
        accountRoles.replaceDiscordRoles(account, GUILD, List.of(1L));
        when(gateway.member(GUILD, DISCORD)).thenThrow(new IllegalStateException("discord down"));
        sync.syncAccount(account);
        assertEquals(List.of(1L), accountRoles.roles(account));

        when(gateway.guild(GUILD)).thenReturn(Optional.empty());
        sync.syncAccount(account);
        sync.syncAll();
        assertEquals(List.of(1L), accountRoles.roles(account));
    }

    @Test
    @DisplayName("An account without Discord linked is not asked about")
    void unlinkedIsSkipped() {
        int lone = accounts.insert(null).id();

        sync.syncAccount(lone);

        assertEquals(List.of(), accountRoles.roles(lone));
        Mockito.verifyNoInteractions(guild);
    }

    @Test
    @DisplayName("A full sync takes the roles of members found and clears those who are gone")
    @SuppressWarnings("unchecked")
    void fullSync() {
        accountRoles.replaceDiscordRoles(other, GUILD, List.of(9L));
        Task<List<Member>> task = mock(Task.class);
        Member found = member(DISCORD, 4L);
        when(task.get()).thenReturn(List.of(found));
        when(guild.retrieveMembersByIds(anyCollection())).thenReturn(task);

        sync.syncAll();

        assertEquals(List.of(4L), accountRoles.roles(account));
        assertEquals(List.of(), accountRoles.roles(other));
    }

    @Test
    @DisplayName("A failing chunk of a full sync leaves its rows alone")
    void fullSyncFailure() {
        accountRoles.replaceDiscordRoles(other, GUILD, List.of(9L));
        when(guild.retrieveMembersByIds(anyCollection())).thenThrow(new IllegalStateException("discord down"));

        sync.syncAll();

        assertEquals(List.of(9L), accountRoles.roles(other));
    }

    @Test
    @DisplayName("A role change from the gateway replaces the linked member's roles, in a mirrored guild only")
    void memberUpdate() {
        sync.memberUpdated(GUILD, DISCORD, List.of(5L, 6L));
        sync.memberUpdated(GUILD + 1, OTHER_DISCORD, List.of(7L));
        sync.memberUpdated(GUILD, 1L, List.of(8L));

        assertEquals(List.of(5L, 6L), accountRoles.roles(account));
        assertEquals(List.of(), accountRoles.roles(other));
    }

    @Test
    @DisplayName("Leaving, a deleted role, and unlinking each take rows away")
    void removals() {
        accountRoles.replaceDiscordRoles(account, GUILD, List.of(1L, 2L));
        accountRoles.replaceDiscordRoles(other, GUILD, List.of(1L, 3L));

        sync.roleDeleted(1L);
        assertEquals(List.of(2L), accountRoles.roles(account));

        sync.memberLeft(GUILD, DISCORD);
        assertEquals(List.of(), accountRoles.roles(account));

        sync.unlinked(other);
        assertEquals(List.of(), accountRoles.roles(other));
    }

    @Test
    @DisplayName("Every linked Discord id is listed with its account")
    void discordAccounts() {
        assertEquals(Map.of(DISCORD, account, OTHER_DISCORD, other), accounts.discordAccounts());
    }
}
