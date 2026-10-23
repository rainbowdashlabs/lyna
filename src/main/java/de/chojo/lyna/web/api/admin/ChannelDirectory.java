/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import de.chojo.lyna.feature.releasepost.service.ChannelTarget;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The channels of a guild as Discord's sidebar shows them: categories, the channels in them, and
 * the threads of each channel. Every entry names its parent by id, so the page can rebuild the
 * tree, and says whether it can be picked itself or is only there to show where something lives.
 */
public final class ChannelDirectory {
    /** How long all archived threads together are waited for. */
    static final long ARCHIVED_WAIT_SECONDS = 10;

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
     * @param parent     the category a channel is in, or the channel a thread is in, by id; null at
     *                   the top
     * @param selectable whether it can be picked, rather than only holding what can
     */
    public record ChannelView(String id, String name, Kind kind, String parent, boolean selectable) {}

    private ChannelDirectory() {}

    /**
     * What a clock can be put on: voice and stage channels, and the categories themselves.
     */
    public static List<ChannelView> renameable(Guild guild) {
        return guild.getChannels().stream()
                .filter(channel -> AdminTimeChannels.RENAMEABLE.contains(channel.getType()))
                .map(channel -> view(channel, Kind.valueOf(channel.getType().name()), true))
                .toList();
    }

    /**
     * Where a post can go: text and announcement channels the bot may write in, forums it may post
     * in, and the threads of either it may write in - the open ones, then the most recent archived
     * ones, which a post reopens. A channel the bot may not write in is still listed, unpickable, when
     * one of its threads can be picked. Categories are listed for every channel to sit in.
     */
    public static List<ChannelView> postable(Guild guild) {
        List<GuildChannel> channels = guild.getChannels();
        Map<IThreadContainer, CompletableFuture<List<ThreadChannel>>> archived = new LinkedHashMap<>();
        for (GuildChannel channel : channels) {
            if (channel instanceof IThreadContainer container && hasThreads(channel)) {
                archived.put(container, ChannelTarget.archivedThreadsAsync(container));
            }
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ARCHIVED_WAIT_SECONDS);

        List<ChannelView> out = new ArrayList<>();
        for (GuildChannel channel : channels) {
            switch (channel.getType()) {
                case CATEGORY -> out.add(view(channel, Kind.CATEGORY, false));
                case TEXT, NEWS, FORUM -> {
                    List<ThreadChannel> threads = archived.containsKey(channel)
                            ? threads((IThreadContainer) channel, archived.get(channel), deadline)
                            : List.of();
                    boolean writable = writable(guild, channel);
                    if (!writable && threads.isEmpty()) continue;
                    out.add(view(channel, Kind.valueOf(channel.getType().name()), writable));
                    threads.forEach(thread -> out.add(
                            new ChannelView(thread.getId(), thread.getName(), Kind.THREAD, channel.getId(), true)));
                }
                default -> {}
            }
        }
        return out;
    }

    private static boolean hasThreads(GuildChannel channel) {
        return switch (channel.getType()) {
            case TEXT, NEWS, FORUM -> true;
            default -> false;
        };
    }

    private static boolean writable(Guild guild, GuildChannel channel) {
        if (channel instanceof ForumChannel forum) {
            return guild.getSelfMember().hasPermission(forum, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND);
        }
        return ((GuildMessageChannel) channel).canTalk();
    }

    private static List<ThreadChannel> threads(
            IThreadContainer container, CompletableFuture<List<ThreadChannel>> archived, long deadline) {
        Map<Long, ThreadChannel> threads = new LinkedHashMap<>();
        container.getThreadChannels().forEach(thread -> threads.put(thread.getIdLong(), thread));
        ChannelTarget.await(archived, deadline).forEach(thread -> threads.putIfAbsent(thread.getIdLong(), thread));
        return threads.values().stream().filter(ThreadChannel::canTalk).toList();
    }

    private static ChannelView view(GuildChannel channel, Kind kind, boolean selectable) {
        String parent = channel instanceof ICategorizableChannel categorized ? categorized.getParentCategoryId() : null;
        return new ChannelView(channel.getId(), channel.getName(), kind, parent, selectable);
    }
}
