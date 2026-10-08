/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.feature.download.entity.DownloadType;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.download.repository.DownloadTypeRepository;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

/**
 * Download types, what {@code /downloads type} manages: the kinds of build a guild offers, each a
 * name, a description and the release type it belongs to.
 *
 * <p>The release type is fixed once made, as the command has it: changing it would move every
 * download of the type to another track, and every license granted access by track with it.
 */
public class AdminDownloads {
    private final GuildAdminGuard guard;
    private final DownloadTypeRepository types;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param usedBy the products with a download of this type, which keep it from being deleted
     */
    public record DownloadTypeView(
            int id, String name, String description, ReleaseType releaseType, List<String> usedBy) {}

    public record DownloadTypeEdit(String name, String description, ReleaseType releaseType) {}

    @Inject
    public AdminDownloads(GuildAdminGuard guard, DownloadTypeRepository types) {
        this.guard = guard;
        this.types = types;
    }

    /**
     * Mounts the routes under the guild being administered.
     */
    public void init() {
        get("download-types", this::listTypes);
        post("download-types", this::createType);
        put("download-types/{typeId}", this::editType);
        delete("download-types/{typeId}", this::deleteType);
    }

    private void listTypes(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        ctx.json(admin.guild().downloadTypes().all().stream()
                .map(type -> new DownloadTypeView(
                        type.id(), type.name(), type.description(), type.releaseType(), types.productsUsing(type.id())))
                .toList());
    }

    private void createType(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        DownloadTypeEdit body = body(ctx);
        if (body == null) return;
        if (body.releaseType() == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result("A download type needs a release type");
            return;
        }
        admin.guild()
                .downloadTypes()
                .create(body.name().strip(), description(body), body.releaseType())
                .ifPresentOrElse(
                        type -> ctx.status(HttpStatus.CREATED)
                                .json(new DownloadTypeView(
                                        type.id(), type.name(), type.description(), type.releaseType(), List.of())),
                        () -> ctx.status(HttpStatus.CONFLICT).result("A download type with that name exists"));
    }

    private void editType(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Optional<DownloadType> type = type(ctx, admin);
        if (type.isEmpty()) return;
        DownloadTypeEdit body = body(ctx);
        if (body == null) return;
        boolean taken = admin.guild().downloadTypes().all().stream()
                .anyMatch(other -> other.id() != type.get().id()
                        && other.name().equalsIgnoreCase(body.name().strip()));
        if (taken) {
            ctx.status(HttpStatus.CONFLICT).result("A download type with that name exists");
            return;
        }
        type.get().name(body.name().strip());
        type.get().description(description(body));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void deleteType(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Optional<DownloadType> type = type(ctx, admin);
        if (type.isEmpty()) return;
        List<String> usedBy = types.productsUsing(type.get().id());
        if (!usedBy.isEmpty()) {
            ctx.status(HttpStatus.CONFLICT)
                    .result("Still offered by %s. Remove those downloads first.".formatted(String.join(", ", usedBy)));
            return;
        }
        type.get().delete();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Optional<DownloadType> type(Context ctx, GuildAdminGuard.GuildAdmin admin) {
        Optional<DownloadType> type;
        try {
            type = admin.guild().downloadTypes().byId(Integer.parseInt(ctx.pathParam("typeId")));
        } catch (NumberFormatException e) {
            type = Optional.empty();
        }
        if (type.isEmpty()) ctx.status(HttpStatus.NOT_FOUND);
        return type;
    }

    private DownloadTypeEdit body(Context ctx) {
        DownloadTypeEdit body;
        try {
            body = json.readValue(ctx.body(), DownloadTypeEdit.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return null;
        }
        if (body == null || body.name() == null || body.name().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("A download type needs a name");
            return null;
        }
        return body;
    }

    private static String description(DownloadTypeEdit body) {
        return body.description() == null ? "" : body.description().strip();
    }
}
