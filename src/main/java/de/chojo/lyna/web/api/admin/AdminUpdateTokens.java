/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.feature.updater.entity.KeyKind;
import de.chojo.lyna.feature.updater.entity.UpdateTokenRequest;
import de.chojo.lyna.feature.updater.service.UpdateTokenService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

/**
 * Deciding on requests for more server keys, adjusting allowances raised before, revoking dashboard
 * keys, and how a product hands dashboard keys out.
 *
 * <p>The request queue, the raised allowances and the held keys are each about one kind of key, named
 * by {@code ?kind=}; without one they are about upgrade tokens, which is all they were before
 * dashboard keys.
 */
public class AdminUpdateTokens {
    private final GuildAdminGuard guard;
    private final UpdateTokenService service;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param granted   keys allowed on top of the default; zero returns the account to the default
     * @param keyTypeId the key type of server keys, upgrade tokens when not named
     */
    public record Allowance(
            int accountId,
            int productId,
            int granted,
            @Nullable Integer keyTypeId) {}

    @Inject
    public AdminUpdateTokens(GuildAdminGuard guard, UpdateTokenService service) {
        this.guard = guard;
        this.service = service;
    }

    /**
     * Mounts the routes under the guild being administered.
     */
    public void init() {
        get("update-tokens/requests", this::pending);
        post("update-tokens/requests/{requestId}/approve", ctx -> decide(ctx, true));
        post("update-tokens/requests/{requestId}/decline", ctx -> decide(ctx, false));
        get("update-tokens/allowances", this::raised);
        put("update-tokens/allowances", this::setAllowance);
        get("update-tokens/keys", this::held);
        delete("update-tokens/keys/{id}", this::revoke);
    }

    private void pending(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        KeyKind kind = kind(ctx);
        if (kind == null) return;
        ctx.json(service.pending(admin.guild().guildId(), kind));
    }

    private void decide(Context ctx, boolean approve) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        int requestId;
        try {
            requestId = Integer.parseInt(ctx.pathParam("requestId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        Optional<UpdateTokenRequest> decided =
                Optional.ofNullable(service.decide(admin.guild().guildId(), requestId, approve, admin.callerDiscordId())
                        .value());
        if (decided.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        ctx.json(decided.get());
    }

    private void raised(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        KeyKind kind = kind(ctx);
        if (kind == null) return;
        ctx.json(service.raised(admin.guild().guildId(), kind));
    }

    private void setAllowance(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Allowance body;
        try {
            body = json.readValue(ctx.body(), Allowance.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        if (body == null || body.granted() < 0) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Give a number of keys, zero or more");
            return;
        }
        boolean set = service.setGranted(
                admin.guild().guildId(), body.accountId(), body.productId(), body.keyTypeId(), body.granted());
        ctx.status(set ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }

    private void held(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        KeyKind kind = kind(ctx);
        if (kind == null) return;
        ctx.json(service.held(admin.guild().guildId(), kind));
    }

    /**
     * Revokes a key on one of the guild's products. Only server keys: an upgrade token buys a download at
     * most, and is its holder's to manage.
     */
    private void revoke(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        int id;
        try {
            id = Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        boolean revoked = service.revokeInGuild(admin.guild().guildId(), id, KeyKind.SERVER);
        ctx.status(revoked ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }

    /**
     * @return the kind the query names, or null when it names none that exists, which has been answered
     */
    private static @Nullable KeyKind kind(Context ctx) {
        Optional<KeyKind> kind = KeyKind.parse(ctx.queryParam("kind"));
        if (kind.isEmpty()) ctx.status(HttpStatus.BAD_REQUEST).result("Unknown kind of key");
        return kind.orElse(null);
    }
}
