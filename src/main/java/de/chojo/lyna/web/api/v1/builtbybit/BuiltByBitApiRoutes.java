/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.builtbybit;

import com.google.inject.Inject;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitOutcome;
import de.chojo.lyna.feature.builtbybit.service.BuiltByBitService;
import de.chojo.lyna.feature.builtbybit.service.BuiltByBitService.KeyAnswer;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * The two addresses BuiltByBit calls: {@code builtbybit/key} for the licence key placeholder and
 * {@code builtbybit/<secret>} for the purchase webhook.
 *
 * <p>The key placeholder writes whatever the body is into the download, so a key is answered as bare
 * text and every refusal with an empty body; BuiltByBit then writes its own apology instead. A
 * purchase for something Lyna does not sell is answered as received, so BuiltByBit stops sending it.
 */
public class BuiltByBitApiRoutes {
    private static final Logger log = getLogger(BuiltByBitApiRoutes.class);

    private final BuiltByBitService builtByBit;

    @Inject
    public BuiltByBitApiRoutes(BuiltByBitService builtByBit) {
        this.builtByBit = builtByBit;
    }

    public void init() {
        path("builtbybit", () -> {
            post("key", this::key);
            post("{secret}", this::purchase);
        });
    }

    private void key(Context ctx) {
        Map<String, String> form = firstValues(ctx.formParamMap());
        if (!builtByBit.keySecretMatches(form.get("secret"))) {
            log.warn("Refused a BuiltByBit key request without the right secret from {}", ctx.ip());
            ctx.status(HttpStatus.FORBIDDEN);
            return;
        }
        form.remove("secret");
        KeyAnswer answer = builtByBit.requestKey(form, form.toString());
        if (answer.outcome() == BuiltByBitOutcome.ISSUED) {
            ctx.contentType(ContentType.TEXT_PLAIN).result(answer.key());
            return;
        }
        ctx.status(status(answer.outcome()));
    }

    private void purchase(Context ctx) {
        if (!builtByBit.webhookSecretMatches(ctx.pathParam("secret"))) {
            log.warn("Refused a BuiltByBit purchase webhook at the wrong address from {}", ctx.ip());
            ctx.status(HttpStatus.FORBIDDEN);
            return;
        }
        BuiltByBitOutcome outcome = builtByBit.receivePurchase(ctx.body());
        ctx.status(
                switch (outcome) {
                    case INVALID, API_UNAVAILABLE -> status(outcome);
                    default -> HttpStatus.OK;
                });
    }

    private static HttpStatus status(BuiltByBitOutcome outcome) {
        return switch (outcome) {
            case ISSUED -> HttpStatus.OK;
            case INVALID -> HttpStatus.BAD_REQUEST;
            case UNKNOWN_PRODUCT -> HttpStatus.NOT_FOUND;
            case BUNDLE, DENIED -> HttpStatus.FORBIDDEN;
            case API_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static Map<String, String> firstValues(Map<String, List<String>> params) {
        Map<String, String> first = new HashMap<>();
        params.forEach((name, values) -> {
            if (!values.isEmpty()) first.put(name, values.getFirst());
        });
        return first;
    }
}
