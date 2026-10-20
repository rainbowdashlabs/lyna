/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.feature.updater.entity.UpdateTokenRequest;
import de.chojo.lyna.feature.updater.service.UpdateTokenService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

/**
 * Deciding on requests for more upgrade tokens, and adjusting allowances raised before.
 */
public class AdminUpdateTokens {
    private final GuildAdminGuard guard;
    private final UpdateTokenService service;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param granted tokens allowed on top of the default; zero returns the account to the default
     */
    public record Allowance(int accountId, int productId, int granted) {}

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
    }

    private void pending(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        ctx.json(service.pending(admin.guild().guildId()));
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
        ctx.json(service.raised(admin.guild().guildId()));
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
            ctx.status(HttpStatus.BAD_REQUEST).result("Give a number of tokens, zero or more");
            return;
        }
        boolean set = service.setGranted(admin.guild().guildId(), body.accountId(), body.productId(), body.granted());
        ctx.status(set ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }
}
