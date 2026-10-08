/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.legacy;

import com.google.inject.Inject;
import de.chojo.lyna.feature.butler.service.ButlerCheckRequest;
import de.chojo.lyna.feature.butler.service.ButlerUpdateService;
import de.chojo.lyna.feature.butler.service.ButlerUpdateService.CheckAnswer;
import de.chojo.lyna.feature.download.service.DownloadFilename;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.web.api.v1.debug.DebugApi;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * The routes UpdateButler served at the root of its host, for plugins that still call them.
 *
 * <p>Status codes and bodies follow Butler's, because the deployed clients act on them: anything but
 * a 200 is read as "no update". Downloads are served only for free products, since these routes have
 * never carried a sign-in; a licensed product's plugin is refused and keeps running what it has.
 */
public class ButlerApi {
    private final ButlerUpdateService updates;
    private final KioskProductRepository kiosk;
    private final DebugApi debug;

    @Inject
    public ButlerApi(ButlerUpdateService updates, KioskProductRepository kiosk, DebugApi debug) {
        this.updates = updates;
        this.kiosk = kiosk;
        this.debug = debug;
    }

    public void init() {
        get("check", this::check);
        get("download", this::download);
        post("debug/v1/submit", debug::submit);
    }

    /**
     * Answers a check in Butler's shape. Also reached from Lyna's own check path, where the newer
     * clients land.
     */
    public void answer(Context ctx, ButlerCheckRequest request) {
        Optional<Product> product = updates.product(request.butlerId());
        if (product.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(CheckAnswer.UNKNOWN);
            return;
        }
        updates.check(product.get(), request.version(), request.devBuild())
                .ifPresentOrElse(
                        answer -> ctx.status(HttpStatus.OK).json(answer),
                        () -> ctx.status(HttpStatus.NOT_FOUND).result("This release does not exist"));
    }

    private void check(Context ctx) {
        ButlerCheckRequest.wellFormed(ctx.queryParamMap())
                .ifPresentOrElse(request -> answer(ctx, request), () -> ctx.status(HttpStatus.BAD_REQUEST)
                        .result("Invalid number"));
    }

    private void download(Context ctx) {
        int butlerId;
        try {
            butlerId = Integer.parseInt(ctx.queryParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid number");
            return;
        }
        String version = ctx.queryParam("version");
        Optional<Product> product = updates.product(butlerId);
        if (product.isEmpty() || version == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Application not found.");
            return;
        }
        if (!kiosk.isFree(product.get().id())) {
            ctx.status(HttpStatus.FORBIDDEN).result("This product is downloaded from Lyna.");
            return;
        }
        Optional<ButlerUpdateService.Build> build = updates.build(product.get(), version.replace("_", " "));
        if (build.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid release");
            return;
        }
        var download = build.get().download();
        var asset = build.get().asset();
        download.downloaded(asset.maven2().version());
        ctx.header("Content-Disposition", "attachment; filename=\"%s\"".formatted(DownloadFilename.of(download, asset)))
                .header("X-Content-Type-Options", "nosniff")
                .contentType(ContentType.APPLICATION_OCTET_STREAM)
                .status(HttpStatus.OK)
                .result(asset.downloadStream().complete());
    }
}
