/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.services;

import de.chojo.lyna.feature.account.service.AccountRoleSchedule;
import de.chojo.lyna.feature.account.service.AccountRoleSync;
import net.dv8tion.jda.api.events.RawGatewayEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Hands what Discord says about members and roles to {@link AccountRoleSync}.
 *
 * <p>Role changes are read from the raw {@code GUILD_MEMBER_UPDATE}. JDA only fires its typed role
 * events for members it has cached, which is almost nobody here; the raw payload carries the full role
 * list of whoever changed, cached or not.
 */
public class AccountRoleListener extends ListenerAdapter {
    private static final String MEMBER_UPDATE = "GUILD_MEMBER_UPDATE";

    private final AccountRoleSync sync;
    private final AccountRoleSchedule schedule;

    public AccountRoleListener(AccountRoleSync sync, AccountRoleSchedule schedule) {
        this.sync = sync;
        this.schedule = schedule;
    }

    @Override
    public void onRawGateway(@NotNull RawGatewayEvent event) {
        if (!MEMBER_UPDATE.equals(event.getType())) return;
        DataObject payload = event.getPayload();
        DataArray roles = payload.getArray("roles");
        List<Long> roleIds = IntStream.range(0, roles.length())
                .mapToObj(roles::getUnsignedLong)
                .toList();
        sync.memberUpdated(
                payload.getUnsignedLong("guild_id"), payload.getObject("user").getUnsignedLong("id"), roleIds);
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        sync.memberLeft(event.getGuild().getIdLong(), event.getUser().getIdLong());
    }

    @Override
    public void onRoleDelete(@NotNull RoleDeleteEvent event) {
        sync.roleDeleted(event.getRole().getIdLong());
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        schedule.runNow();
    }
}
