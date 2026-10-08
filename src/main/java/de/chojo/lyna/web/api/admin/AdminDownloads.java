/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.DownloadType;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.download.repository.DownloadRepository;
import de.chojo.lyna.feature.download.repository.DownloadTypeRepository;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.gateway.Gateway;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

/**
 * What {@code /downloads} manages: the kinds of build a guild offers, each product's downloads - where
 * in Nexus its builds of a kind live - and which Discord roles reach which release type.
 *
 * <p>The release type is fixed once made, as the command has it: changing it would move every
 * download of the type to another track, and every license granted access by track with it.
 */
public class AdminDownloads {
    private final GuildAdminGuard guard;
    private final DownloadTypeRepository types;
    private final DownloadRepository downloads;
    private final Gateway gateway;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param usedBy the products with a download of this type, which keep it from being deleted
     */
    public record DownloadTypeView(
            int id, String name, String description, ReleaseType releaseType, List<String> usedBy) {}

    public record DownloadTypeEdit(String name, String description, ReleaseType releaseType) {}

    /**
     * @param latestVersion the newest version Nexus holds for these coordinates, or null when it holds
     *                      none or could not be asked
     */
    public record DownloadView(
            int typeId,
            String typeName,
            ReleaseType releaseType,
            String repository,
            String groupId,
            String artifactId,
            String classifier,
            String latestVersion) {}

    public record DownloadEdit(
            Integer typeId, String repository, String groupId, String artifactId, String classifier) {}

    /**
     * @param roleId   as text, since a Discord id is larger than a JavaScript number holds exactly
     * @param roleName the role's name when a gateway knows it, otherwise null
     */
    public record RoleAccessView(String roleId, String roleName, ReleaseType releaseType) {}

    public record RoleAccessGrant(String roleId, ReleaseType releaseType) {}

    public record RoleView(String id, String name) {}

    @Inject
    public AdminDownloads(
            GuildAdminGuard guard, DownloadTypeRepository types, DownloadRepository downloads, Gateway gateway) {
        this.guard = guard;
        this.types = types;
        this.downloads = downloads;
        this.gateway = gateway;
    }

    /**
     * Mounts the routes under the guild being administered.
     */
    public void init() {
        get("download-types", this::listTypes);
        post("download-types", this::createType);
        put("download-types/{typeId}", this::editType);
        delete("download-types/{typeId}", this::deleteType);
        get("roles", this::listRoles);
        get("products/{productId}/downloads", this::listDownloads);
        post("products/{productId}/downloads", this::createDownload);
        put("products/{productId}/downloads/{typeId}", this::editDownload);
        delete("products/{productId}/downloads/{typeId}", this::deleteDownload);
        get("products/{productId}/role-access", this::listRoleAccess);
        post("products/{productId}/role-access", this::grantRoleAccess);
        delete("products/{productId}/role-access/{roleId}/{releaseType}", this::revokeRoleAccess);
    }

    /**
     * The guild's roles, for picking one. Empty without a gateway, where a role is named by id.
     */
    private void listRoles(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        ctx.json(gateway.guild(admin.guild().guildId())
                .map(guild -> guild.getRoles().stream()
                        .filter(role -> !role.isPublicRole() && !role.isManaged())
                        .map(role -> new RoleView(role.getId(), role.getName()))
                        .toList())
                .orElse(List.of()));
    }

    private void listDownloads(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Product product = guard.product(ctx, admin);
        if (product == null) return;
        ctx.json(product.downloads().downloads().stream()
                .map(AdminDownloads::view)
                .toList());
    }

    /**
     * Adds a download. Coordinates Nexus holds nothing for are taken anyway - a first release may
     * not exist yet - and the answer's missing latest version says so.
     */
    private void createDownload(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Product product = guard.product(ctx, admin);
        if (product == null) return;
        DownloadEdit body = downloadBody(ctx);
        if (body == null) return;
        Optional<DownloadType> type = body.typeId() == null
                ? Optional.empty()
                : admin.guild().downloadTypes().byId(body.typeId());
        if (type.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Choose a download type of this guild");
            return;
        }
        product.downloads()
                .create(
                        type.get(),
                        body.repository().strip(),
                        body.groupId().strip(),
                        body.artifactId().strip(),
                        classifier(body))
                .ifPresentOrElse(download -> ctx.status(HttpStatus.CREATED).json(view(download)), () -> ctx.status(
                                HttpStatus.CONFLICT)
                        .result("This product already has a download of that type"));
    }

    private void editDownload(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Product product = guard.product(ctx, admin);
        if (product == null) return;
        Optional<Download> download = download(ctx, product);
        if (download.isEmpty()) return;
        DownloadEdit body = downloadBody(ctx);
        if (body == null) return;
        download.get().repository(body.repository().strip());
        download.get().groupId(body.groupId().strip());
        download.get().artifactId(body.artifactId().strip());
        download.get().classifier(classifier(body));
        ctx.json(view(product.downloads().byType(download.get().type().id()).orElseThrow()));
    }

    private void deleteDownload(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Product product = guard.product(ctx, admin);
        if (product == null) return;
        Optional<Download> download = download(ctx, product);
        if (download.isEmpty()) return;
        download.get().delete();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listRoleAccess(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Product product = guard.product(ctx, admin);
        if (product == null) return;
        var guild = gateway.guild(admin.guild().guildId());
        ctx.json(downloads.roleAccess(product.id()).stream()
                .map(access -> new RoleAccessView(
                        Long.toString(access.roleId()),
                        guild.map(g -> g.getRoleById(access.roleId()))
                                .map(role -> role.getName())
                                .orElse(null),
                        access.releaseType()))
                .toList());
    }

    private void grantRoleAccess(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Product product = guard.product(ctx, admin);
        if (product == null) return;
        RoleAccessGrant body;
        long roleId;
        try {
            body = json.readValue(ctx.body(), RoleAccessGrant.class);
            roleId = Long.parseLong(body.roleId().strip());
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Name a role by its id and a release type");
            return;
        }
        if (body.releaseType() == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Name a role by its id and a release type");
            return;
        }
        boolean known = gateway.guild(admin.guild().guildId())
                .map(guild -> guild.getRoleById(roleId) != null)
                .orElse(true);
        if (!known) {
            ctx.status(HttpStatus.BAD_REQUEST).result("This guild has no role with that id");
            return;
        }
        downloads.grantRole(roleId, product.id(), body.releaseType());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void revokeRoleAccess(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Product product = guard.product(ctx, admin);
        if (product == null) return;
        long roleId;
        ReleaseType releaseType;
        try {
            roleId = Long.parseLong(ctx.pathParam("roleId"));
            releaseType = ReleaseType.valueOf(ctx.pathParam("releaseType"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        ctx.status(
                downloads.revokeRole(roleId, product.id(), releaseType) ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }

    private static DownloadView view(Download download) {
        String latest;
        try {
            latest = download.latestAssets().stream()
                    .findFirst()
                    .map(asset -> asset.maven2().version())
                    .orElse(null);
        } catch (RuntimeException e) {
            latest = null;
        }
        return new DownloadView(
                download.type().id(),
                download.type().name(),
                download.type().releaseType(),
                download.repository(),
                download.groupId(),
                download.artifactId(),
                download.classifier(),
                latest);
    }

    private Optional<Download> download(Context ctx, Product product) {
        Optional<Download> download;
        try {
            download = product.downloads().byType(Integer.parseInt(ctx.pathParam("typeId")));
        } catch (NumberFormatException e) {
            download = Optional.empty();
        }
        if (download.isEmpty()) ctx.status(HttpStatus.NOT_FOUND);
        return download;
    }

    private DownloadEdit downloadBody(Context ctx) {
        DownloadEdit body;
        try {
            body = json.readValue(ctx.body(), DownloadEdit.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return null;
        }
        if (body == null || blank(body.repository()) || blank(body.groupId()) || blank(body.artifactId())) {
            ctx.status(HttpStatus.BAD_REQUEST).result("A download needs a repository, a group id and an artifact id");
            return null;
        }
        return body;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String classifier(DownloadEdit body) {
        return blank(body.classifier()) ? null : body.classifier().strip();
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
