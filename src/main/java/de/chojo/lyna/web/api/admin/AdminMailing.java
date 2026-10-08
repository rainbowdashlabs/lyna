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
import de.chojo.lyna.feature.mail.entity.Mailing;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.purchase.service.PurchaseService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * Making a product's mailing and sending its license mail by hand, as {@code /mailing create} and
 * {@code /mailing send} do. The mail itself is written on the mailing page.
 */
public class AdminMailing {
    private final GuildAdminGuard guard;
    private final LicenseService licenses;
    private final PurchaseService purchases;
    private final ObjectMapper json = new ObjectMapper();

    public record MailingCreate(String name) {}

    public record MailingCreated(int id, String name) {}

    /**
     * @param name how the mail greets the recipient
     */
    public record MailingSend(String address, String name) {}

    @Inject
    public AdminMailing(GuildAdminGuard guard, LicenseService licenses, PurchaseService purchases) {
        this.guard = guard;
        this.licenses = licenses;
        this.purchases = purchases;
    }

    /**
     * Mounts the routes under the guild being administered.
     */
    public void init() {
        post("products/{productId}/mailing", this::create);
        post("products/{productId}/mailing/send", this::send);
    }

    private void create(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Product product = guard.product(ctx, admin);
        if (product == null) return;
        MailingCreate body;
        try {
            body = json.readValue(ctx.body(), MailingCreate.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        if (body == null || body.name() == null || body.name().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("The mail needs a name for the product");
            return;
        }
        if (product.mailings().get().isPresent()) {
            ctx.status(HttpStatus.CONFLICT).result("This product has a mailing already");
            return;
        }
        Mailing mailing = product.mailings().create(body.name().strip(), "");
        ctx.status(HttpStatus.CREATED).json(new MailingCreated(mailing.id(), mailing.name()));
    }

    /**
     * Issues a license for the address and mails it there, with access to stable builds. An address
     * that has a license for the product already is sent that one again, never a second.
     */
    private void send(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Product product = guard.product(ctx, admin);
        if (product == null) return;
        MailingSend body;
        try {
            body = json.readValue(ctx.body(), MailingSend.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        if (body == null
                || body.address() == null
                || !body.address().contains("@")
                || body.name() == null
                || body.name().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Name the recipient and their email address");
            return;
        }
        Optional<Mailing> mailing = product.mailings().get();
        if (mailing.isEmpty()) {
            ctx.status(HttpStatus.CONFLICT).result("This product has no mailing to send");
            return;
        }
        String address = body.address().strip();
        Optional<License> license = product.createLicense(address);
        if (license.isEmpty()) {
            ctx.status(HttpStatus.CONFLICT).result("A license already exists for this address");
            return;
        }
        licenses.grantAccess(license.get(), ReleaseType.STABLE);
        purchases.announce(mailing.get(), license.get(), body.name().strip(), address);
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
