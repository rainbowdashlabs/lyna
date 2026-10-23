/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasepost.service;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Somewhere a post about a product goes: a channel messages are written in, a thread, or a forum
 * that gets a new post each time.
 *
 * <p>Every post mentions nobody. What it carries is partly written by whoever opened an issue or
 * wrote a release, and a post must not become a way to ping a role.
 */
public sealed interface ChannelTarget permits ChannelTarget.Messages, ChannelTarget.Forum {
    /** How many archived threads of a channel are looked through, newest first. */
    int ARCHIVED_THREADS = 25;

    /** The longest title Discord takes for a forum post. */
    int POST_TITLE_LIMIT = 100;

    /**
     * @param title      what a forum post is called; unused elsewhere
     * @param components buttons under the post, none for no row
     * @param failure    told when Discord refuses the post
     */
    void post(String title, MessageEmbed embed, List<ActionRow> components, Consumer<Throwable> failure);

    /**
     * Finds a channel by id in the guilds given. A thread that has been archived is not cached, so
     * an id the cache does not know is looked for among the archived threads of every channel that
     * holds threads.
     */
    static Optional<ChannelTarget> resolve(Collection<Guild> guilds, long channelId) {
        if (channelId == 0) return Optional.empty();
        for (Guild guild : guilds) {
            GuildChannel channel = guild.getGuildChannelById(channelId);
            if (channel instanceof ForumChannel forum) return Optional.of(new Forum(forum));
            if (channel instanceof GuildMessageChannel messages) return Optional.of(new Messages(messages));
        }
        for (Guild guild : guilds) {
            for (IThreadContainer container : threadContainers(guild)) {
                for (ThreadChannel thread : archivedThreads(container)) {
                    if (thread.getIdLong() == channelId) return Optional.of(new Messages(thread));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The channels of a guild that hold threads: text, announcement and forum channels.
     */
    static List<IThreadContainer> threadContainers(Guild guild) {
        return guild.getChannels().stream()
                .filter(IThreadContainer.class::isInstance)
                .map(IThreadContainer.class::cast)
                .toList();
    }

    /**
     * The newest archived threads of a channel, asked for without waiting. A channel that does not
     * answer has none.
     */
    static CompletableFuture<List<ThreadChannel>> archivedThreadsAsync(IThreadContainer container) {
        try {
            return container
                    .retrieveArchivedPublicThreadChannels()
                    .takeAsync(ARCHIVED_THREADS)
                    .exceptionally(failure -> List.of());
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * The newest archived threads of a channel, or none when Discord does not answer in time.
     */
    static List<ThreadChannel> archivedThreads(IThreadContainer container) {
        return await(archivedThreadsAsync(container), System.nanoTime() + TimeUnit.SECONDS.toNanos(10));
    }

    /**
     * Waits for archived threads until the deadline, given in {@link System#nanoTime()}, and gives
     * none when it passes.
     */
    static List<ThreadChannel> await(CompletableFuture<List<ThreadChannel>> threads, long deadline) {
        try {
            return threads.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            return List.of();
        }
    }

    /**
     * A channel messages are written into: text, announcements, or a thread. An archived thread is
     * reopened first, unless a moderator locked it, in which case Discord refuses the post.
     */
    record Messages(GuildMessageChannel channel) implements ChannelTarget {
        @Override
        public void post(String title, MessageEmbed embed, List<ActionRow> components, Consumer<Throwable> failure) {
            if (channel instanceof ThreadChannel thread && thread.isArchived() && !thread.isLocked()) {
                thread.getManager().setArchived(false).queue(reopened -> send(embed, components, failure), failure);
                return;
            }
            send(embed, components, failure);
        }

        private void send(MessageEmbed embed, List<ActionRow> components, Consumer<Throwable> failure) {
            MessageCreateAction action = channel.sendMessageEmbeds(embed);
            if (!components.isEmpty()) action = action.addComponents(components);
            action.setAllowedMentions(List.of()).queue(success -> {}, failure);
        }
    }

    /**
     * A forum, where every post is a thread of its own.
     */
    record Forum(ForumChannel forum) implements ChannelTarget {
        @Override
        public void post(String title, MessageEmbed embed, List<ActionRow> components, Consumer<Throwable> failure) {
            MessageCreateBuilder message =
                    new MessageCreateBuilder().setEmbeds(embed).setAllowedMentions(List.of());
            if (!components.isEmpty()) message.setComponents(components);
            forum.createForumPost(GithubEmbeds.cut(title, POST_TITLE_LIMIT), message.build())
                    .queue(success -> {}, failure);
        }
    }
}
