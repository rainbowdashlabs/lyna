/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.entity.UpdateTokenRequest;
import de.chojo.lyna.feature.updater.service.UpdateTokenService;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Outcome;
import de.chojo.lyna.web.api.auth.Auth;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

/**
 * The account's upgrade tokens: making, naming, reissuing and revoking them, and asking for more.
 *
 * <p>A refusal is answered with the name of its {@link UpdateTokenService.Refusal} as the body, so the
 * page can say it in its own words.
 */
public class AccountUpdateTokens {
    private final Auth auth;
    private final UpdateTokenService service;
    private final ProductLookup products;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param used    tokens the account holds for the product
     * @param allowed how many it may hold
     * @param pending whether a request for more is waiting
     */
    public record ProductAllowance(int productId, String productName, int used, int allowed, boolean pending) {}

    public record Overview(
            List<UpdateToken> tokens, List<ProductAllowance> products, List<UpdateTokenRequest> requests) {}

    public record Create(int productId, String name) {}

    public record Rename(String name) {}

    public record RequestMore(int productId, int amount, String reason) {}

    @Inject
    public AccountUpdateTokens(Auth auth, UpdateTokenService service, ProductLookup products) {
        this.auth = auth;
        this.service = service;
        this.products = products;
    }

    /**
     * Mounts the routes under the account's update tokens.
     */
    public void init() {
        get(this::overview);
        post(this::create);
        post("requests", this::requestMore);
        put("{id}", this::rename);
        delete("{id}", this::revoke);
        post("{id}/reissue", this::reissue);
    }

    private void overview(Context ctx) {
        Optional<JwtService.Verified> session = session(ctx);
        if (session.isEmpty()) return;
        int accountId = session.get().accountId();
        List<UpdateTokenRequest> requests = service.requests(accountId);
        List<ProductAllowance> allowances = service.allowances(accountId).stream()
                .map(allowance -> new ProductAllowance(
                        allowance.productId(),
                        products.byId(allowance.productId()).map(Product::name).orElse("?"),
                        allowance.used(),
                        allowance.allowed(),
                        requests.stream()
                                .anyMatch(request -> request.productId() == allowance.productId()
                                        && request.status() == UpdateTokenRequest.Status.PENDING)))
                .toList();
        ctx.json(new Overview(service.tokens(accountId), allowances, requests));
    }

    private void create(Context ctx) {
        Optional<JwtService.Verified> session = session(ctx);
        if (session.isEmpty()) return;
        Create body = read(ctx, Create.class);
        if (body == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result(UpdateTokenService.Refusal.INVALID_NAME.name());
            return;
        }
        answer(ctx, service.create(session.get().accountId(), body.productId(), body.name()), HttpStatus.CREATED);
    }

    private void rename(Context ctx) {
        Optional<JwtService.Verified> session = session(ctx);
        if (session.isEmpty()) return;
        Integer id = id(ctx);
        if (id == null) return;
        Rename body = read(ctx, Rename.class);
        answer(ctx, service.rename(session.get().accountId(), id, body == null ? null : body.name()), HttpStatus.OK);
    }

    private void revoke(Context ctx) {
        Optional<JwtService.Verified> session = session(ctx);
        if (session.isEmpty()) return;
        Integer id = id(ctx);
        if (id == null) return;
        ctx.status(service.revoke(session.get().accountId(), id) ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }

    private void reissue(Context ctx) {
        Optional<JwtService.Verified> session = session(ctx);
        if (session.isEmpty()) return;
        Integer id = id(ctx);
        if (id == null) return;
        answer(ctx, service.reissue(session.get().accountId(), id), HttpStatus.OK);
    }

    private void requestMore(Context ctx) {
        Optional<JwtService.Verified> session = session(ctx);
        if (session.isEmpty()) return;
        RequestMore body = read(ctx, RequestMore.class);
        if (body == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result(UpdateTokenService.Refusal.INVALID_REQUEST.name());
            return;
        }
        answer(
                ctx,
                service.requestMore(session.get().accountId(), body.productId(), body.amount(), body.reason()),
                HttpStatus.CREATED);
    }

    private static void answer(Context ctx, Outcome<?> outcome, HttpStatus success) {
        if (outcome.ok()) {
            ctx.status(success).json(outcome.value());
            return;
        }
        HttpStatus status =
                switch (outcome.refusal()) {
                    case NO_ACCESS -> HttpStatus.FORBIDDEN;
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case LIMIT_REACHED, PENDING -> HttpStatus.CONFLICT;
                    default -> HttpStatus.BAD_REQUEST;
                };
        ctx.status(status).result(outcome.refusal().name());
    }

    private Optional<JwtService.Verified> session(Context ctx) {
        Optional<JwtService.Verified> session = auth.currentSession(ctx);
        if (session.isEmpty()) ctx.status(HttpStatus.UNAUTHORIZED);
        return session;
    }

    private static @Nullable Integer id(Context ctx) {
        try {
            return Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
    }

    private <T> @Nullable T read(Context ctx, Class<T> type) {
        try {
            return json.readValue(ctx.body(), type);
        } catch (Exception e) {
            return null;
        }
    }
}
