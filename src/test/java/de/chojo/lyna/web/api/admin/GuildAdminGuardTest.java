/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import de.chojo.lyna.configuration.TestConf;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.instance.repository.InstanceOperatorRepository;
import de.chojo.lyna.gateway.Gateway;
import de.chojo.lyna.web.api.auth.Auth;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who has Manage Server on a guild, asked of Discord when the bot's cache does not hold the member.
 */
class GuildAdminGuardTest {
    private static final long GUILD = 1L;
    private static final long OWNER = 2L;

    @Test
    @DisplayName("A member the cache does not hold is asked of Discord, so an uncached owner still gets in")
    void uncachedMemberIsAsked() {
        Guild guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(GUILD);
        when(guild.getMemberById(OWNER)).thenReturn(null);
        Member owner = mock(Member.class);
        when(owner.hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        Gateway gateway = mock(Gateway.class);
        when(gateway.member(GUILD, OWNER)).thenReturn(Optional.of(owner));
        GuildAdminGuard guard = new GuildAdminGuard(
                mock(Auth.class),
                TestConf.defaults(),
                mock(AccountRepository.class),
                mock(Guilds.class),
                mock(InstanceOperatorRepository.class),
                gateway);

        assertTrue(guard.hasGuildAdmin(OWNER, guild));
        assertFalse(guard.hasGuildAdmin(3L, guild), "somebody Discord does not know as a member is nobody");
        assertFalse(guard.hasGuildAdmin(null, guild), "an account without Discord has no membership to check");
    }
}
