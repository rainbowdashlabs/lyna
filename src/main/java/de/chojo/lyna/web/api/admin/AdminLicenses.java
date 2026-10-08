/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.feature.purchase.repository.KoFiProductRepository;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.put;

/**
 * Ending licenses and setting which release types one reaches, as {@code /license delete} and
 * {@code /license downloads} do, and unlinking Ko-fi codes.
 */
public class AdminLicenses {
    private final GuildAdminGuard guard;
    private final LicenseService licenses;
    private final KoFiProductRepository kofi;
    private final ObjectMapper json = new ObjectMapper();

    public record LicenseAccess(List<ReleaseType> releaseTypes) {}

    @Inject
    public AdminLicenses(GuildAdminGuard guard, LicenseService licenses, KoFiProductRepository kofi) {
        this.guard = guard;
        this.licenses = licenses;
        this.kofi = kofi;
    }

    /**
     * Mounts the routes under the guild being administered.
     */
    public void init() {
        delete("licenses/{licenseId}", this::deleteLicense);
        get("licenses/{licenseId}/access", this::access);
        put("licenses/{licenseId}/access", this::setAccess);
        delete("kofi/{linkCode}", this::removeKofi);
    }

    /**
     * Ends a license. Whoever held it loses the product's role unless something else grants it.
     */
    private void deleteLicense(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Optional<License> license = license(ctx, admin);
        if (license.isEmpty()) return;
        licenses.delete(license.get());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void access(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        license(ctx, admin).ifPresent(license -> ctx.json(new LicenseAccess(licenses.access(license))));
    }

    /**
     * Makes the license reach exactly the release types named: those missing are granted, those not
     * named are revoked.
     */
    private void setAccess(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Optional<License> license = license(ctx, admin);
        if (license.isEmpty()) return;
        LicenseAccess body;
        try {
            body = json.readValue(ctx.body(), LicenseAccess.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        Set<ReleaseType> wanted = body == null
                        || body.releaseTypes() == null
                        || body.releaseTypes().isEmpty()
                ? EnumSet.noneOf(ReleaseType.class)
                : EnumSet.copyOf(body.releaseTypes());
        for (ReleaseType type : ReleaseType.values()) {
            if (wanted.contains(type)) licenses.grantAccess(license.get(), type);
            else licenses.revokeAccess(license.get(), type);
        }
        ctx.json(new LicenseAccess(licenses.access(license.get())));
    }

    private void removeKofi(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        boolean removed = kofi.remove(admin.guild().guildId(), ctx.pathParam("linkCode"));
        ctx.status(removed ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }

    private Optional<License> license(Context ctx, GuildAdminGuard.GuildAdmin admin) {
        Optional<License> license;
        try {
            license = admin.guild().licenses().byId(Integer.parseInt(ctx.pathParam("licenseId")));
        } catch (NumberFormatException e) {
            license = Optional.empty();
        }
        if (license.isEmpty()) ctx.status(HttpStatus.NOT_FOUND);
        return license;
    }
}
