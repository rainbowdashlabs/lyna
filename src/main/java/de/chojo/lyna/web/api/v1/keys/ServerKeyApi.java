/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.keys;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.feature.updater.entity.ProductKeyType;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.service.ServerKeyService;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Holding;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Outcome;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Refusal;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * Where server keys are used: a server refreshing its key, and a key type's gateway verifying one.
 *
 * <p>{@code POST /api/v1/keys/refresh} with {@code Authorization: Bearer <server key>}
 * answers {@code 200 {key, refreshAt}}: the key the server is to hold from now on, the same one until
 * {@code refreshAt} has passed and its successor after. A refusal is answered as {@code {reason}}: 401
 * for {@code UNKNOWN}, {@code EXPIRED} and {@code RETIRED}, 403 for {@code NO_ACCESS}, 429 for
 * {@code TOO_SOON}.
 *
 * <p>{@code POST /api/v1/keys/verify} with {@code Authorization: Bearer <gateway secret>} and
 * {@code {key}} answers {@code 200}: either {@code {valid: true, product, type, kind, keyId, name,
 * refreshAt, rotated}} or {@code {valid: false, reason}}. The gateway secret is that of one key type,
 * and only keys of that type are valid to it; any other key is {@code UNKNOWN}. {@code type} is the key
 * type's slug. {@code kind} is {@code BOUGHT} or {@code TRIAL};
 * {@code keyId} stays the same across rotations, so the gateway recognises a server after a refresh;
 * {@code rotated} says the key presented was already retired and is accepted inside the grace. The
 * reasons are {@code UNKNOWN}, {@code EXPIRED}, {@code NO_ACCESS} and {@code RETIRED}. Nothing about
 * the account is answered. A secret of no key type is answered 401; a body without a key is answered
 * 400.
 */
public class ServerKeyApi {
    private final ServerKeyService service;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param key       the key to hold from now on
     * @param refreshAt when the next refresh will trade it for its successor
     */
    public record Refreshed(String key, Instant refreshAt) {}

    /**
     * @param reason why the key is refused, as a client is to act on it
     */
    public record Refused(String reason) {}

    /**
     * The gateway asking about a key.
     */
    public record VerifyRequest(@Nullable String key) {}

    /**
     * The answer to the gateway. A valid key carries everything but {@code reason}; a refused one
     * carries only {@code valid} and {@code reason}.
     *
     * @param product   the product the key is for
     * @param type      the slug of the key type it is of
     * @param kind      {@code BOUGHT} when the account has the product, {@code TRIAL} when it only took the
     *                  trial
     * @param keyId     the entry the key belongs to, the same across rotations
     * @param name      what the key's owner named the server
     * @param refreshAt when the server's next refresh will trade the key for its successor
     * @param rotated   whether the key presented was already retired and is accepted inside the grace
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Verification(
            boolean valid,
            @Nullable Integer product,
            @Nullable String type,
            @Nullable String kind,
            @Nullable Integer keyId,
            @Nullable String name,
            @Nullable Instant refreshAt,
            @Nullable Boolean rotated,
            @Nullable String reason) {
        static Verification of(ServerKeyService.Verified verified) {
            UpdateToken entry = verified.entry();
            String kind = verified.holding() == Holding.TRIAL ? "TRIAL" : "BOUGHT";
            return new Verification(
                    true,
                    entry.productId(),
                    verified.type().slug(),
                    kind,
                    entry.id(),
                    entry.name(),
                    entry.refreshAt(),
                    verified.rotated(),
                    null);
        }

        static Verification refused(String reason) {
            return new Verification(false, null, null, null, null, null, null, null, reason);
        }
    }

    @Inject
    public ServerKeyApi(ServerKeyService service) {
        this.service = service;
    }

    /**
     * Mounts the routes under version one of the API.
     */
    public void init() {
        path("keys", () -> {
            post("refresh", this::refresh);
            post("verify", this::verify);
        });
    }

    private void refresh(Context ctx) {
        Outcome<ServerKeyService.Refreshed> outcome = service.refresh(bearer(ctx));
        if (outcome.ok()) {
            UpdateToken entry = outcome.value().entry();
            ctx.json(new Refreshed(entry.token(), entry.refreshAt()));
            return;
        }
        String reason = reason(outcome.refusal());
        HttpStatus status =
                switch (reason) {
                    case "NO_ACCESS" -> HttpStatus.FORBIDDEN;
                    case "TOO_SOON" -> HttpStatus.TOO_MANY_REQUESTS;
                    default -> HttpStatus.UNAUTHORIZED;
                };
        ctx.status(status).json(new Refused(reason));
    }

    private void verify(Context ctx) {
        Optional<ProductKeyType> type = service.gateway(bearer(ctx));
        if (type.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        VerifyRequest body;
        try {
            body = json.readValue(ctx.body(), VerifyRequest.class);
        } catch (Exception e) {
            body = null;
        }
        if (body == null || body.key() == null || body.key().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Name the key to verify");
            return;
        }
        Outcome<ServerKeyService.Verified> outcome =
                service.verify(type.get(), body.key().strip());
        ctx.json(outcome.ok() ? Verification.of(outcome.value()) : Verification.refused(reason(outcome.refusal())));
    }

    /**
     * Names a refusal in the words of a gateway: a key type that was switched off is the same to a
     * server as an account that lost the product, and a key given up is retired.
     */
    private static String reason(Refusal refusal) {
        return switch (refusal) {
            case EXPIRED -> "EXPIRED";
            case NO_ACCESS, NOT_OFFERED -> "NO_ACCESS";
            case REUSED -> "RETIRED";
            case TOO_SOON -> "TOO_SOON";
            default -> "UNKNOWN";
        };
    }

    private static @Nullable String bearer(Context ctx) {
        String header = ctx.header("Authorization");
        return header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length()).trim()
                : null;
    }
}
