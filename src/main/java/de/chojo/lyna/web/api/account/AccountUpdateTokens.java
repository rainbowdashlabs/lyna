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
import de.chojo.lyna.feature.updater.entity.KeyKind;
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
import java.util.stream.Stream;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

/**
 * The account's server keys, upgrade tokens and dashboard keys alike: making, naming, reissuing and
 * revoking them, and asking for more. A body names the kind it means; one that names none means
 * upgrade tokens, which is all there was before dashboard keys.
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
     * @param used    keys of the kind the account holds for the product
     * @param allowed how many it may hold
     * @param pending whether a request for more is waiting
     * @param trial   whether the account only took the product's trial, which allows one dashboard key
     *                and no request for more
     */
    public record ProductAllowance(
            int productId, String productName, KeyKind kind, int used, int allowed, boolean pending, boolean trial) {}

    public record Overview(
            List<UpdateToken> tokens, List<ProductAllowance> products, List<UpdateTokenRequest> requests) {}

    /**
     * @param kind the kind of key to make, upgrade token when not named
     */
    public record Create(
            int productId, String name, @Nullable String kind) {}

    public record Rename(String name) {}

    /**
     * @param kind the kind of key to ask more of, upgrade tokens when not named
     */
    public record RequestMore(
            int productId,
            int amount,
            String reason,
            @Nullable String kind) {}

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
        List<ProductAllowance> allowances = Stream.of(KeyKind.values())
                .flatMap(kind -> service.allowances(accountId, kind).stream())
                .map(allowance -> new ProductAllowance(
                        allowance.productId(),
                        products.byId(allowance.productId()).map(Product::name).orElse("?"),
                        allowance.kind(),
                        allowance.used(),
                        allowance.allowed(),
                        requests.stream()
                                .anyMatch(request -> request.productId() == allowance.productId()
                                        && request.kind() == allowance.kind()
                                        && request.status() == UpdateTokenRequest.Status.PENDING),
                        allowance.trial()))
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
        Optional<KeyKind> kind = KeyKind.parse(body.kind());
        if (kind.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).result(UpdateTokenService.Refusal.INVALID_REQUEST.name());
            return;
        }
        answer(
                ctx,
                service.create(session.get().accountId(), body.productId(), kind.get(), body.name()),
                HttpStatus.CREATED);
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
        Optional<KeyKind> kind = body == null ? Optional.empty() : KeyKind.parse(body.kind());
        if (kind.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).result(UpdateTokenService.Refusal.INVALID_REQUEST.name());
            return;
        }
        answer(
                ctx,
                service.requestMore(
                        session.get().accountId(), body.productId(), kind.get(), body.amount(), body.reason()),
                HttpStatus.CREATED);
    }

    private static void answer(Context ctx, Outcome<?> outcome, HttpStatus success) {
        if (outcome.ok()) {
            ctx.status(success).json(outcome.value());
            return;
        }
        HttpStatus status =
                switch (outcome.refusal()) {
                    case NO_ACCESS, NOT_OFFERED -> HttpStatus.FORBIDDEN;
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
