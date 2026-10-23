/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.google.inject.Inject;
import de.chojo.lyna.gateway.Gateway;
import io.javalin.http.Context;
import net.dv8tion.jda.api.entities.Guild;

import java.util.List;
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

    /**
     * @param connected whether a bot answered; without one the list is empty and means nothing
     */
    public record Channels(boolean connected, List<ChannelDirectory.ChannelView> channels) {}

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
                guild.map(g -> renaming ? ChannelDirectory.renameable(g) : ChannelDirectory.postable(g))
                        .orElse(List.of())));
    }
}
