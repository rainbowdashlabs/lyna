/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.feature.updater.entity.ProductKeyType;
import de.chojo.lyna.feature.updater.service.ProductKeyTypeService;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Outcome;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.Nullable;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

/**
 * The kinds of server key a product of the guild hands out, on the product's Server keys tab.
 *
 * <p>The gateway secret of a key type is answered here, to the guild's admins, because they are the
 * ones who configure the gateway with it.
 */
public class AdminKeyTypes {
    private final GuildAdminGuard guard;
    private final ProductKeyTypeService keyTypes;
    private final ObjectMapper json = new ObjectMapper();

    @Inject
    public AdminKeyTypes(GuildAdminGuard guard, ProductKeyTypeService keyTypes) {
        this.guard = guard;
        this.keyTypes = keyTypes;
    }

    /**
     * Mounts the routes under the guild being administered.
     */
    public void init() {
        get("products/{productId}/key-types", this::list);
        post("products/{productId}/key-types", this::create);
        put("key-types/{id}", this::update);
        delete("key-types/{id}", this::remove);
        post("key-types/{id}/secret", this::replaceSecret);
    }

    private void list(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        var product = guard.product(ctx, admin);
        if (product == null) return;
        ctx.json(keyTypes.forProduct(product.id()));
    }

    private void create(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        var product = guard.product(ctx, admin);
        if (product == null) return;
        answer(ctx, keyTypes.create(admin.guild().guildId(), product.id(), draft(ctx)), HttpStatus.CREATED);
    }

    private void update(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Integer id = id(ctx);
        if (id == null) return;
        answer(ctx, keyTypes.update(admin.guild().guildId(), id, draft(ctx)), HttpStatus.OK);
    }

    private void replaceSecret(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Integer id = id(ctx);
        if (id == null) return;
        answer(ctx, keyTypes.replaceSecret(admin.guild().guildId(), id), HttpStatus.OK);
    }

    /**
     * Ends a key type and every key of it.
     */
    private void remove(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Integer id = id(ctx);
        if (id == null) return;
        ctx.status(keyTypes.delete(admin.guild().guildId(), id) ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }

    private static void answer(Context ctx, Outcome<ProductKeyType> outcome, HttpStatus success) {
        if (outcome.ok()) {
            ctx.status(success).json(outcome.value());
            return;
        }
        switch (outcome.refusal()) {
            case INVALID_REQUEST ->
                ctx.status(HttpStatus.BAD_REQUEST)
                        .result("A slug is 1 to 32 lower-case letters, digits and dashes; a name up to %d characters;"
                                        .formatted(ProductKeyType.Draft.MAX_NAME_LENGTH)
                                + " both intervals 1 to %d days; both allowances 0 to %d keys"
                                        .formatted(ProductKeyType.Draft.MAX_DAYS, ProductKeyType.Draft.MAX_ALLOWANCE));
            case PENDING -> ctx.status(HttpStatus.CONFLICT).result("The product already has a key type by that slug");
            default -> ctx.status(HttpStatus.NOT_FOUND);
        }
    }

    private @Nullable ProductKeyType.Draft draft(Context ctx) {
        try {
            return json.readValue(ctx.body(), ProductKeyType.Draft.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable Integer id(Context ctx) {
        try {
            return Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
    }
}
