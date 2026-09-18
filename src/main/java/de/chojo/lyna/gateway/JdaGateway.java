/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.gateway;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The gateway, through a connected shard manager.
 *
 * <p>Takes a supplier rather than the manager itself, because the bot is built after most of what
 * asks it questions. Nothing calls the supplier until somebody actually asks.
 */
public class JdaGateway implements Gateway {
    private final Supplier<ShardManager> shardManager;

    public JdaGateway(Supplier<ShardManager> shardManager) {
        this.shardManager = shardManager;
    }

    @Override
    public boolean connected() {
        return shardManager.get() != null;
    }

    @Override
    public Optional<Guild> guild(long guildId) {
        ShardManager manager = shardManager.get();
        return manager == null ? Optional.empty() : Optional.ofNullable(manager.getGuildById(guildId));
    }

    @Override
    public List<Guild> guilds() {
        ShardManager manager = shardManager.get();
        return manager == null ? List.of() : manager.getGuilds();
    }

    @Override
    public Optional<Member> member(long guildId, long discordId) {
        Optional<Guild> guild = guild(guildId);
        if (guild.isEmpty()) return Optional.empty();
        Member cached = guild.get().getMemberById(discordId);
        if (cached != null) return Optional.of(cached);
        try {
            return Optional.ofNullable(guild.get().retrieveMemberById(discordId).complete());
        } catch (ErrorResponseException e) {
            if (e.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER
                    || e.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
