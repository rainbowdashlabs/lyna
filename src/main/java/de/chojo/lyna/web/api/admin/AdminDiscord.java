/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.google.inject.Inject;
import de.chojo.lyna.feature.releasepost.service.ChannelTarget;
import de.chojo.lyna.gateway.Gateway;
import io.javalin.http.Context;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.get;

/**
 * The guild's roles and channels by name, so an operator picks one rather than typing an id.
 *
 * <p>Without a bot there is nobody to ask, and the answer says so: the page then falls back to
 * asking for the id.
 */
public class AdminDiscord {
    private final GuildAdminGuard guard;
    private final Gateway gateway;

    public record RoleView(String id, String name) {}

    /**
     * @param connected whether a bot answered; without one the list is empty and means nothing
     */
    public record Roles(boolean connected, List<RoleView> roles) {}

    public enum Kind {
        TEXT,
        NEWS,
        FORUM,
        THREAD,
        VOICE,
        STAGE,
        CATEGORY
    }

    /**
     * @param parent the forum a thread is in, by name; null for anything else
     */
    public record ChannelView(String id, String name, Kind kind, String parent) {}

    /**
     * @param connected whether a bot answered; without one the list is empty and means nothing
     */
    public record Channels(boolean connected, List<ChannelView> channels) {}

    @Inject
    public AdminDiscord(GuildAdminGuard guard, Gateway gateway) {
        this.guard = guard;
        this.gateway = gateway;
    }

    /**
     * Mounts the routes under the guild being administered.
     */
    public void init() {
        get("roles", this::roles);
        get("channels", this::channels);
    }

    /**
     * The roles a product or the admin area can be tied to: neither {@code @everyone}, which
     * everybody has, nor a role an integration manages, which nobody can hand out.
     */
    private void roles(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Optional<Guild> guild = gateway.guild(admin.guild().guildId());
        ctx.json(new Roles(
                guild.isPresent(),
                guild.map(g -> g.getRoles().stream()
                                .filter(role -> !role.isPublicRole() && !role.isManaged())
                                .map(role -> new RoleView(role.getId(), role.getName()))
                                .toList())
                        .orElse(List.of())));
    }

    /**
     * Channels for one purpose: {@code ?for=renaming} lists those a clock can be put on, anything
     * else those a post can go to.
     */
    private void channels(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Optional<Guild> guild = gateway.guild(admin.guild().guildId());
        boolean renaming = "renaming".equals(ctx.queryParam("for"));
        ctx.json(new Channels(
                guild.isPresent(),
                guild.map(g -> renaming ? renameable(g) : postable(g)).orElse(List.of())));
    }

    private static List<ChannelView> renameable(Guild guild) {
        return guild.getChannels().stream()
                .filter(channel -> AdminTimeChannels.RENAMEABLE.contains(channel.getType()))
                .map(channel -> new ChannelView(
                        channel.getId(),
                        channel.getName(),
                        Kind.valueOf(channel.getType().name()),
                        null))
                .toList();
    }

    /**
     * Text and announcement channels the bot may write in, and forums it may post in, in the order
     * Discord shows them. Each forum is followed by its threads: the open ones, then the most recent
     * archived ones, which a post reopens.
     */
    private static List<ChannelView> postable(Guild guild) {
        List<ChannelView> out = new ArrayList<>();
        for (GuildChannel channel : guild.getChannels()) {
            switch (channel.getType()) {
                case TEXT, NEWS -> {
                    if (((GuildMessageChannel) channel).canTalk()) {
                        out.add(new ChannelView(
                                channel.getId(),
                                channel.getName(),
                                Kind.valueOf(channel.getType().name()),
                                null));
                    }
                }
                case FORUM -> {
                    ForumChannel forum = (ForumChannel) channel;
                    if (!guild.getSelfMember().hasPermission(forum, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)) {
                        continue;
                    }
                    out.add(new ChannelView(forum.getId(), forum.getName(), Kind.FORUM, null));
                    threads(guild, forum)
                            .forEach(thread -> out.add(
                                    new ChannelView(thread.getId(), thread.getName(), Kind.THREAD, forum.getName())));
                }
                default -> {}
            }
        }
        return out;
    }

    private static List<ThreadChannel> threads(Guild guild, ForumChannel forum) {
        Map<Long, ThreadChannel> threads = new LinkedHashMap<>();
        guild.getThreadChannels().stream()
                .filter(thread -> thread.getParentChannel().getIdLong() == forum.getIdLong())
                .forEach(thread -> threads.put(thread.getIdLong(), thread));
        ChannelTarget.archivedThreads(forum).forEach(thread -> threads.putIfAbsent(thread.getIdLong(), thread));
        return threads.values().stream().filter(ThreadChannel::canTalk).toList();
    }
}
