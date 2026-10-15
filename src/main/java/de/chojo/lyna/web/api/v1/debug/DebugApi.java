/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.debug;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.inject.Inject;
import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.configuration.elements.DebugReports;
import de.chojo.lyna.feature.debug.service.DebugReportService;
import de.chojo.lyna.feature.debug.service.DebugReportService.Submitted;
import de.chojo.lyna.feature.debug.service.ReportedVersions;
import de.chojo.lyna.feature.debug.service.UploadThrottle;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * Debug reports: uploaded by plugins, read and deleted by whoever holds the links.
 *
 * <p>An upload is mostly a server log, far larger than anything else this server takes, so the
 * server-wide request limit is raised to {@link DebugReports#maxUploadBytes()} for it. A gzipped upload is held to
 * the same limit once unpacked, read no further than that, so a small body cannot unpack into a large one.
 */
public class DebugApi {
    private final DebugReportService reports;
    private final UploadThrottle throttle;
    private final ReportedVersions versions;
    private final DebugReports settings;
    private final Api api;

    @Inject
    public DebugApi(
            DebugReportService reports,
            UploadThrottle throttle,
            ReportedVersions versions,
            DebugReports settings,
            Api api) {
        this.reports = reports;
        this.throttle = throttle;
        this.versions = versions;
        this.settings = settings;
        this.api = api;
    }

    public void init() {
        path("debug", () -> {
            post("submit", this::submit);
            get("{readKey}", this::read);
            get("{readKey}/latest", this::latest);
            get("{readKey}/sections/{position}", this::section);
            delete("{deleteKey}", this::remove);
        });
    }

    /**
     * Takes an upload in either format, gzipped or not. A report in the second format is answered with its links; one
     * in the first with UpdateButler's keys, which is what its clients read. Those clients look only at the status of
     * a refusal, so every refusal is a {@link Problem}.
     */
    public void submit(Context ctx) {
        if (!throttle.admit(clientAddress(ctx))) {
            ctx.header("Retry-After", String.valueOf(settings.uploadIntervalSeconds()));
            ctx.status(HttpStatus.TOO_MANY_REQUESTS)
                    .json(new Problem(
                            "rate_limited",
                            "One report per %d seconds. Please wait.".formatted(settings.uploadIntervalSeconds())));
            return;
        }
        Optional<String> body = body(ctx);
        if (body.isEmpty()) {
            ctx.status(HttpStatus.CONTENT_TOO_LARGE)
                    .json(new Problem(
                            "too_large",
                            "A report may be at most %d bytes unpacked.".formatted(settings.maxUploadBytes())));
            return;
        }
        switch (reports.submit(body.get())) {
            case Submitted.Stored stored
            when stored.format() == 1 -> ctx.status(HttpStatus.OK).json(stored.keys());
            case Submitted.Stored stored -> ctx.status(HttpStatus.OK).json(receipt(stored));
            case Submitted.Refused refused ->
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT).json(new Problem("invalid", refused.reason()));
        }
    }

    /**
     * What a client is told about a stored report.
     *
     * @param readUrl   the viewer
     * @param deleteUrl the page that deletes it
     * @param expires   when it is deleted on its own
     */
    public record Receipt(
            String readKey,
            String deleteKey,
            String readUrl,
            String deleteUrl,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expires) {}

    /**
     * @param error   a stable code a client can act on
     * @param message a sentence for whoever sent the report
     */
    public record Problem(String error, String message) {}

    private Receipt receipt(Submitted.Stored stored) {
        String read = stored.keys().hash();
        String delete = stored.keys().deletionHash();
        return new Receipt(
                read,
                delete,
                "%s/debug/%s".formatted(api.url(), read),
                "%s/debug/delete/%s".formatted(api.url(), delete),
                stored.expires());
    }

    /**
     * The body as text, unpacked when it is gzipped.
     *
     * @return nothing when it is larger than {@link DebugReports#maxUploadBytes()} unpacked
     */
    private Optional<String> body(Context ctx) {
        String encoding = ctx.header(Header.CONTENT_ENCODING);
        if (encoding == null || !encoding.strip().equalsIgnoreCase("gzip")) return Optional.of(ctx.body());
        try (var in = new GZIPInputStream(new ByteArrayInputStream(ctx.bodyAsBytes()))) {
            byte[] unpacked = in.readNBytes(settings.maxUploadBytes() + 1);
            if (unpacked.length > settings.maxUploadBytes()) return Optional.empty();
            return Optional.of(new String(unpacked, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.of("");
        }
    }

    private void latest(Context ctx) {
        reports.read(ctx.pathParam("readKey"))
                .flatMap(report -> versions.latest(report.pluginName(), report.pluginVersion()))
                .ifPresentOrElse(
                        latest -> ctx.header("Cache-Control", "no-store").json(latest),
                        () -> ctx.status(HttpStatus.NO_CONTENT));
    }

    private void read(Context ctx) {
        reports.read(ctx.pathParam("readKey"))
                .ifPresentOrElse(
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
        reports.section(ctx.pathParam("readKey"), position)
                .ifPresentOrElse(
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
