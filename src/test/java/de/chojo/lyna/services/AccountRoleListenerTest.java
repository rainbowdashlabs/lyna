/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.services;

import de.chojo.lyna.feature.account.service.AccountRoleSchedule;
import de.chojo.lyna.feature.account.service.AccountRoleSync;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.RawGatewayEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the gateway says about members and roles reaches the sync.
 */
class AccountRoleListenerTest {
    private final AccountRoleSync sync = mock(AccountRoleSync.class);
    private final AccountRoleSchedule schedule = mock(AccountRoleSchedule.class);
    private final AccountRoleListener listener = new AccountRoleListener(sync, schedule);

    private static RawGatewayEvent raw(String type, String json) {
        RawGatewayEvent event = mock(RawGatewayEvent.class);
        when(event.getType()).thenReturn(type);
        when(event.getPayload()).thenReturn(DataObject.fromJson(json));
        return event;
    }

    @Test
    @DisplayName("A raw member update hands over the full role list")
    void memberUpdate() {
        listener.onRawGateway(raw("GUILD_MEMBER_UPDATE", """
                {"guild_id": "11", "user": {"id": "22"}, "roles": ["33", "44"]}"""));

        verify(sync).memberUpdated(11L, 22L, List.of(33L, 44L));
    }

    @Test
    @DisplayName("Any other raw event is ignored")
    void otherRawEvents() {
        listener.onRawGateway(raw("MESSAGE_CREATE", "{}"));

        verify(sync, never()).memberUpdated(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("Leaving, a deleted role and a connected gateway each reach the sync")
    void otherEvents() {
        Guild guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(11L);
        User user = mock(User.class);
        when(user.getIdLong()).thenReturn(22L);
        GuildMemberRemoveEvent left = mock(GuildMemberRemoveEvent.class);
        when(left.getGuild()).thenReturn(guild);
        when(left.getUser()).thenReturn(user);
        Role role = mock(Role.class);
        when(role.getIdLong()).thenReturn(33L);
        RoleDeleteEvent deleted = mock(RoleDeleteEvent.class);
        when(deleted.getRole()).thenReturn(role);

        listener.onGuildMemberRemove(left);
        listener.onRoleDelete(deleted);
        listener.onReady(mock(ReadyEvent.class));

        verify(sync).memberLeft(11L, 22L);
        verify(sync).roleDeleted(33L);
        verify(schedule).runNow();
    }
}
