/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.releasepost.service.ChannelTarget;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.requests.restaction.ForumPostAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Where a post about a product goes, and how it gets there for each kind of channel.
 */
class ChannelTargetTest {
    private static final MessageEmbed EMBED =
            new EmbedBuilder().setTitle("Widget v1").build();
    private static final List<ActionRow> BUTTONS =
            List.of(ActionRow.of(Button.link("https://example.invalid", "Get it")));

    private final Guild guild = mock(Guild.class);
    private final List<Throwable> failures = new ArrayList<>();
    private final Consumer<Throwable> failed = failures::add;

    @SuppressWarnings("unchecked")
    private static MessageCreateAction sendable(GuildMessageChannel channel) {
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(action);
        when(action.addComponents(any(Collection.class))).thenReturn(action);
        when(action.setAllowedMentions(any())).thenReturn(action);
        return action;
    }

    private static ThreadChannel archived(long id, boolean locked) {
        ThreadChannel thread = mock(ThreadChannel.class);
        when(thread.getIdLong()).thenReturn(id);
        when(thread.isArchived()).thenReturn(true);
        when(thread.isLocked()).thenReturn(locked);
        return thread;
    }

    private static ForumChannel forumWithArchived(CompletableFuture<List<ThreadChannel>> threads) {
        ForumChannel forum = mock(ForumChannel.class);
        ThreadChannelPaginationAction archived = mock(ThreadChannelPaginationAction.class);
        when(forum.retrieveArchivedPublicThreadChannels()).thenReturn(archived);
        when(archived.takeAsync(ChannelTarget.ARCHIVED_THREADS)).thenReturn(threads);
        return forum;
    }

    @Test
    @DisplayName("No channel set is nowhere, and an id nobody has is nowhere either")
    void nowhere() {
        when(guild.getChannels()).thenReturn(List.of());

        assertTrue(ChannelTarget.resolve(List.of(guild), 0).isEmpty());
        assertTrue(ChannelTarget.resolve(List.of(guild), 5).isEmpty());
    }

    @Test
    @DisplayName("A text channel, a forum and an archived thread are each found by id")
    void resolving() {
        GuildMessageChannel text = mock(GuildMessageChannel.class);
        ForumChannel forum = forumWithArchived(CompletableFuture.completedFuture(List.of(archived(9, false))));
        when(guild.getGuildChannelById(1)).thenReturn(text);
        when(guild.getGuildChannelById(2)).thenReturn(forum);
        when(guild.getGuildChannelById(3)).thenReturn(mock(Category.class));
        when(guild.getChannels()).thenReturn(List.of(text, forum));

        assertSame(
                text,
                ((ChannelTarget.Messages)
                                ChannelTarget.resolve(List.of(guild), 1).orElseThrow())
                        .channel());
        assertSame(
                forum,
                ((ChannelTarget.Forum) ChannelTarget.resolve(List.of(guild), 2).orElseThrow()).forum());
        assertInstanceOf(
                ChannelTarget.Messages.class,
                ChannelTarget.resolve(List.of(guild), 9).orElseThrow());
        assertTrue(ChannelTarget.resolve(List.of(guild), 3).isEmpty(), "a category takes no posts");
    }

    @Test
    @DisplayName("An archived thread of a text channel is found by id too")
    void archivedTextThread() {
        TextChannel text = mock(TextChannel.class);
        ThreadChannel thread = archived(12, false);
        ThreadChannelPaginationAction pages = mock(ThreadChannelPaginationAction.class);
        when(text.retrieveArchivedPublicThreadChannels()).thenReturn(pages);
        when(pages.takeAsync(ChannelTarget.ARCHIVED_THREADS))
                .thenReturn(CompletableFuture.completedFuture(List.of(thread)));
        when(guild.getChannels()).thenReturn(List.of(text));

        assertInstanceOf(
                ChannelTarget.Messages.class,
                ChannelTarget.resolve(List.of(guild), 12).orElseThrow());
    }

    @Test
    @DisplayName("A forum that does not answer has no archived threads to offer")
    void archivedThreadsFailQuietly() {
        ForumChannel failing =
                forumWithArchived(CompletableFuture.failedFuture(new IllegalStateException("no access")));
        assertEquals(List.of(), ChannelTarget.archivedThreads(failing));

        ForumChannel pending = forumWithArchived(new CompletableFuture<>());
        Thread.currentThread().interrupt();
        assertEquals(List.of(), ChannelTarget.archivedThreads(pending));
        assertTrue(Thread.interrupted(), "the interrupt is kept for whoever asked");
    }

    @Test
    @DisplayName("A channel the bot may not look into, or one that answers too late, has no archived threads")
    void archivedThreadsRefusedOrLate() {
        TextChannel refusing = mock(TextChannel.class);
        when(refusing.retrieveArchivedPublicThreadChannels()).thenThrow(new IllegalStateException("missing access"));
        assertEquals(List.of(), ChannelTarget.archivedThreads(refusing));

        assertEquals(List.of(), ChannelTarget.await(new CompletableFuture<>(), System.nanoTime()));
    }

    @Test
    @DisplayName("A channel gets the embed and its buttons, and mentions nobody")
    @SuppressWarnings("unchecked")
    void textChannelPost() {
        GuildMessageChannel text = mock(GuildMessageChannel.class);
        MessageCreateAction action = sendable(text);

        new ChannelTarget.Messages(text).post("ignored", EMBED, BUTTONS, failed);

        verify(action).addComponents(BUTTONS);
        verify(action).setAllowedMentions(List.of());
        ArgumentCaptor<Consumer<Throwable>> failure = ArgumentCaptor.forClass(Consumer.class);
        verify(action).queue(any(), failure.capture());
        failure.getValue().accept(new RuntimeException("missing access"));
        assertEquals(1, failures.size());
    }

    @Test
    @DisplayName("A post without buttons adds no empty row")
    @SuppressWarnings("unchecked")
    void textChannelWithoutButtons() {
        GuildMessageChannel text = mock(GuildMessageChannel.class);
        MessageCreateAction action = sendable(text);

        new ChannelTarget.Messages(text).post("ignored", EMBED, List.of(), failed);

        verify(action, never()).addComponents(any(Collection.class));
        verify(action).queue(any(), any());
    }

    @Test
    @DisplayName("An archived thread is reopened before the post, a locked one is not touched")
    @SuppressWarnings("unchecked")
    void archivedThreadIsReopened() {
        ThreadChannel open = archived(9, false);
        ThreadChannelManager manager = mock(ThreadChannelManager.class);
        when(open.getManager()).thenReturn(manager);
        when(manager.setArchived(false)).thenReturn(manager);
        MessageCreateAction action = sendable(open);

        new ChannelTarget.Messages(open).post("ignored", EMBED, List.of(), failed);

        ArgumentCaptor<Consumer<Void>> reopened = ArgumentCaptor.forClass(Consumer.class);
        verify(manager).queue(reopened.capture(), any());
        verify(action, never()).queue(any(), any());
        reopened.getValue().accept(null);
        verify(action).queue(any(), any());

        ThreadChannel locked = archived(10, true);
        MessageCreateAction lockedAction = sendable(locked);
        new ChannelTarget.Messages(locked).post("ignored", EMBED, List.of(), failed);
        verify(locked, never()).getManager();
        verify(lockedAction).queue(any(), any());
    }

    @Test
    @DisplayName("A forum gets a post of its own, titled and cut to what Discord takes")
    @SuppressWarnings("unchecked")
    void forumPost() {
        ForumChannel forum = mock(ForumChannel.class);
        ForumPostAction action = mock(ForumPostAction.class);
        when(forum.createForumPost(anyString(), any(MessageCreateData.class))).thenReturn(action);

        new ChannelTarget.Forum(forum).post("x".repeat(150), EMBED, BUTTONS, failed);
        new ChannelTarget.Forum(forum).post("Widget v1", EMBED, List.of(), failed);

        ArgumentCaptor<String> titles = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MessageCreateData> messages = ArgumentCaptor.forClass(MessageCreateData.class);
        verify(forum, Mockito.times(2)).createForumPost(titles.capture(), messages.capture());
        assertEquals(
                ChannelTarget.POST_TITLE_LIMIT, titles.getAllValues().get(0).length());
        assertEquals("Widget v1", titles.getAllValues().get(1));
        assertEquals(1, messages.getAllValues().get(0).getComponents().size());
        assertEquals(0, messages.getAllValues().get(1).getComponents().size());
        assertEquals(List.of(EMBED), messages.getAllValues().get(1).getEmbeds());
        verify(action, Mockito.times(2)).queue(any(), any());
    }
}
