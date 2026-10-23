/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import de.chojo.lyna.feature.releasepost.service.ChannelTarget;
import de.chojo.lyna.web.api.admin.ChannelDirectory.ChannelView;
import de.chojo.lyna.web.api.admin.ChannelDirectory.Kind;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The guild's channels as the picker lays them out: by category, with threads under their channel.
 */
class ChannelDirectoryTest {
    private final Guild guild = mock(Guild.class);
    private final SelfMember self = mock(SelfMember.class);

    {
        when(guild.getSelfMember()).thenReturn(self);
    }

    private static <T extends GuildChannel> T channel(Class<T> type, ChannelType kind, String id, String name) {
        T channel = mock(type);
        when(channel.getType()).thenReturn(kind);
        when(channel.getId()).thenReturn(id);
        when(channel.getName()).thenReturn(name);
        return channel;
    }

    private static ThreadChannel thread(long id, String name, boolean canTalk) {
        ThreadChannel thread = mock(ThreadChannel.class);
        when(thread.getIdLong()).thenReturn(id);
        when(thread.getId()).thenReturn(Long.toString(id));
        when(thread.getName()).thenReturn(name);
        when(thread.canTalk()).thenReturn(canTalk);
        return thread;
    }

    private static void threads(IThreadContainer container, List<ThreadChannel> open, List<ThreadChannel> archived) {
        when(container.getThreadChannels()).thenReturn(open);
        ThreadChannelPaginationAction action = mock(ThreadChannelPaginationAction.class);
        when(container.retrieveArchivedPublicThreadChannels()).thenReturn(action);
        when(action.takeAsync(ChannelTarget.ARCHIVED_THREADS)).thenReturn(CompletableFuture.completedFuture(archived));
    }

    @Test
    @DisplayName("Channels name their category and threads their channel, open threads before archived ones")
    void postable() {
        Category releases = channel(Category.class, ChannelType.CATEGORY, "1", "Releases");
        TextChannel news = channel(TextChannel.class, ChannelType.TEXT, "2", "news");
        when(news.getParentCategoryId()).thenReturn("1");
        when(news.canTalk()).thenReturn(true);
        threads(
                news,
                List.of(thread(20, "open", true)),
                List.of(thread(21, "archived", true), thread(20, "open", true)));
        ForumChannel support = channel(ForumChannel.class, ChannelType.FORUM, "3", "support");
        when(self.hasPermission(eq(support), any(Permission[].class))).thenReturn(true);
        threads(support, List.of(), List.of(thread(30, "locked out", false)));
        when(guild.getChannels()).thenReturn(List.of(support, releases, news));

        assertEquals(
                List.of(
                        new ChannelView("3", "support", Kind.FORUM, null, true),
                        new ChannelView("1", "Releases", Kind.CATEGORY, null, false),
                        new ChannelView("2", "news", Kind.TEXT, "1", true),
                        new ChannelView("20", "open", Kind.THREAD, "2", true),
                        new ChannelView("21", "archived", Kind.THREAD, "2", true)),
                ChannelDirectory.postable(guild));
    }

    @Test
    @DisplayName("A channel the bot cannot write in is kept only to hold a thread it can")
    void unwritableChannels() {
        TextChannel silent = channel(TextChannel.class, ChannelType.TEXT, "4", "silent");
        threads(silent, List.of(), List.of());
        TextChannel holder = channel(TextChannel.class, ChannelType.TEXT, "5", "holder");
        threads(holder, List.of(thread(50, "writable", true)), List.of());
        VoiceChannel voice = channel(VoiceChannel.class, ChannelType.VOICE, "6", "voice");
        when(guild.getChannels()).thenReturn(List.of(silent, holder, voice));

        assertEquals(
                List.of(
                        new ChannelView("5", "holder", Kind.TEXT, null, false),
                        new ChannelView("50", "writable", Kind.THREAD, "5", true)),
                ChannelDirectory.postable(guild));
    }

    @Test
    @DisplayName("A clock goes on a voice channel or a category, and both can be picked")
    void renameable() {
        Category lobby = channel(Category.class, ChannelType.CATEGORY, "1", "Lobby");
        VoiceChannel clock = channel(VoiceChannel.class, ChannelType.VOICE, "2", "clock");
        when(clock.getParentCategoryId()).thenReturn("1");
        TextChannel text = channel(TextChannel.class, ChannelType.TEXT, "3", "text");
        when(guild.getChannels()).thenReturn(List.of(text, lobby, clock));

        assertEquals(
                List.of(
                        new ChannelView("1", "Lobby", Kind.CATEGORY, null, true),
                        new ChannelView("2", "clock", Kind.VOICE, "1", true)),
                ChannelDirectory.renameable(guild));
    }
}
