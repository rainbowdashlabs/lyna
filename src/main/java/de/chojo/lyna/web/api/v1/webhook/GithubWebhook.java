/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.webhook;

import com.google.inject.Inject;
import de.chojo.lyna.feature.releasepost.service.ReleasePostService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * Where GitHub delivers a product's release events.
 */
public class GithubWebhook {
    private final ReleasePostService releases;

    @Inject
    public GithubWebhook(ReleasePostService releases) {
        this.releases = releases;
    }

    public void init() {
        post("webhook/github/{token}", this::receive);
    }

    private void receive(Context ctx) {
        var outcome = releases.receive(
                ctx.pathParam("token"),
                ctx.header("X-GitHub-Event"),
                ctx.header("X-Hub-Signature-256"),
                ctx.bodyAsBytes());
        switch (outcome) {
            case UNKNOWN -> ctx.status(HttpStatus.NOT_FOUND);
            case FORBIDDEN -> ctx.status(HttpStatus.UNAUTHORIZED).result("Signature does not match");
            case PONG -> ctx.status(HttpStatus.OK).result("pong");
            case IGNORED -> ctx.status(HttpStatus.NO_CONTENT);
            case ANNOUNCED -> ctx.status(HttpStatus.OK).result("announced");
        }
    }
}
