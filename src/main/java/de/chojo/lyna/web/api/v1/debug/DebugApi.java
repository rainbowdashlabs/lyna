/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.debug;

import com.google.inject.Inject;
import de.chojo.lyna.configuration.elements.DebugReports;
import de.chojo.lyna.feature.debug.service.DebugReportService;
import de.chojo.lyna.feature.debug.service.UploadThrottle;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * Debug reports: uploaded by plugins, read and deleted by whoever holds the links.
 *
 * <p>The body is read from the stream up to the configured limit rather than through
 * {@link Context#body()}, whose limit is the server-wide one and far smaller than a server log.
 */
public class DebugApi {
    private final DebugReportService reports;
    private final UploadThrottle throttle;
    private final DebugReports settings;

    @Inject
    public DebugApi(DebugReportService reports, UploadThrottle throttle, DebugReports settings) {
        this.reports = reports;
        this.throttle = throttle;
        this.settings = settings;
    }

    public void init() {
        path("debug", () -> {
            post("submit", this::submit);
            get("{readKey}", this::read);
            get("{readKey}/sections/{position}", this::section);
            delete("{deleteKey}", this::remove);
        });
    }

    /**
     * Takes an upload. Also mounted at UpdateButler's old path, where the deployed clients send it.
     */
    public void submit(Context ctx) throws IOException {
        if (!throttle.admit(clientAddress(ctx))) {
            ctx.status(HttpStatus.TOO_MANY_REQUESTS).result("You are rate limited. Please wait.");
            return;
        }
        String body;
        try (InputStream in = ctx.bodyInputStream()) {
            byte[] bytes = in.readNBytes(settings.maxUploadBytes() + 1);
            if (bytes.length > settings.maxUploadBytes()) {
                ctx.status(HttpStatus.CONTENT_TOO_LARGE).result("The report is too large.");
                return;
            }
            body = new String(bytes, StandardCharsets.UTF_8);
        }
        reports.submit(body).ifPresentOrElse(
                keys -> ctx.status(HttpStatus.OK).json(keys),
                () -> ctx.status(HttpStatus.UNPROCESSABLE_CONTENT).result("That is not a debug report."));
    }

    private void read(Context ctx) {
        reports.read(ctx.pathParam("readKey")).ifPresentOrElse(
                report -> ctx.header("Cache-Control", "no-store").json(report),
                () -> ctx.status(HttpStatus.NOT_FOUND).result("No such report."));
    }

    private void section(Context ctx) {
        int position;
        try {
            position = Integer.parseInt(ctx.pathParam("position"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        reports.section(ctx.pathParam("readKey"), position).ifPresentOrElse(
                content -> ctx.header("Cache-Control", "no-store")
                        .header("X-Content-Type-Options", "nosniff")
                        .contentType(ContentType.TEXT_PLAIN.getMimeType() + "; charset=utf-8")
                        .result(content),
                () -> ctx.status(HttpStatus.NOT_FOUND).result("No such section."));
    }

    private void remove(Context ctx) {
        ctx.status(reports.delete(ctx.pathParam("deleteKey")) ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }

    /**
     * The address the upload came from, as the proxy in front of the backend reports it.
     */
    private static String clientAddress(Context ctx) {
        String forwarded = ctx.header("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].strip();
        String real = ctx.header("X-Real-IP");
        if (real != null && !real.isBlank()) return real.strip();
        return ctx.ip();
    }
}
