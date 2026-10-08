/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.feature.account.entity.AccountLicense;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.service.AccountService;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.repository.LicenseLookup;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.web.api.auth.Auth;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * Redeeming a license key and handing a license to somebody else, as {@code /register} and
 * {@code /registrations transfer} do on Discord.
 */
public class AccountLicenseActions {
    private final Auth auth;
    private final LicenseLookup lookup;
    private final LicenseService licenseService;
    private final AccountLicenseRepository licenses;
    private final AccountService accounts;
    private final ObjectMapper json = new ObjectMapper();

    public record Redeem(String key) {}

    /**
     * @param productId what the key was for
     */
    public record Redeemed(int licenseId, int productId, String productName) {}

    /**
     * @param subject the receiving account, by username or by an address it has proved
     */
    public record Transfer(String subject) {}

    @Inject
    public AccountLicenseActions(
            Auth auth,
            LicenseLookup lookup,
            LicenseService licenseService,
            AccountLicenseRepository licenses,
            AccountService accounts) {
        this.auth = auth;
        this.lookup = lookup;
        this.licenseService = licenseService;
        this.licenses = licenses;
        this.accounts = accounts;
    }

    /**
     * Mounts the routes under the account's licenses.
     */
    public void init() {
        post("redeem", this::redeem);
        post("{id}/transfer", this::transfer);
    }

    /**
     * Gives the account the license a key names. Refused, with the reason the command gives, for a
     * key nobody issued, one somebody holds, and a product the account holds a license for already.
     */
    private void redeem(Context ctx) {
        Optional<JwtService.Verified> session = session(ctx);
        if (session.isEmpty()) return;
        Redeem body = read(ctx, Redeem.class);
        if (body == null || body.key() == null || body.key().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Enter a license key");
            return;
        }
        Optional<License> license = lookup.byKey(body.key().strip());
        if (license.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result("That is not a license key");
            return;
        }
        int accountId = session.get().accountId();
        int productId = license.get().product().id();
        if (licenses.owned(accountId).stream().anyMatch(owned -> owned.productId() == productId)) {
            ctx.status(HttpStatus.CONFLICT).result("You already hold a license for this product");
            return;
        }
        if (!licenseService.claim(license.get(), accountId)) {
            ctx.status(HttpStatus.CONFLICT).result("This license is already claimed");
            return;
        }
        ctx.status(HttpStatus.CREATED)
                .json(new Redeemed(
                        license.get().id(), productId, license.get().product().name()));
    }

    /**
     * Hands a license the account owns to another account, ending every share of it first.
     */
    private void transfer(Context ctx) {
        Optional<JwtService.Verified> session = session(ctx);
        if (session.isEmpty()) return;
        int licenseId;
        try {
            licenseId = Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        Optional<AccountLicense> owned = licenses.forHolder(
                        licenseId, session.get().accountId())
                .filter(license -> license.role() == AccountLicense.Role.OWNER);
        if (owned.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        Transfer body = read(ctx, Transfer.class);
        String subject =
                body == null || body.subject() == null ? "" : body.subject().strip();
        if (subject.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Name the account by username or address");
            return;
        }
        var target = subject.contains("@") ? accounts.findByEmail(subject) : accounts.findByUsername(subject);
        if (target.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result("Nobody here goes by that");
            return;
        }
        if (target.get().id() == session.get().accountId()) {
            ctx.status(HttpStatus.CONFLICT).result("You hold this license already");
            return;
        }
        if (licenses.owned(target.get().id()).stream()
                .anyMatch(other -> other.productId() == owned.get().productId())) {
            ctx.status(HttpStatus.CONFLICT).result("They already hold a license for this product");
            return;
        }
        Optional<License> license = lookup.byKey(
                licenses.keyForHolder(licenseId, session.get().accountId()).orElse(""));
        if (license.isEmpty()
                || !licenseService.transfer(license.get(), target.get().id())) {
            ctx.status(HttpStatus.CONFLICT).result("The license could not be transferred");
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Optional<JwtService.Verified> session(Context ctx) {
        Optional<JwtService.Verified> session = auth.currentSession(ctx);
        if (session.isEmpty()) ctx.status(HttpStatus.UNAUTHORIZED);
        return session;
    }

    private <T> T read(Context ctx, Class<T> type) {
        try {
            return json.readValue(ctx.body(), type);
        } catch (Exception e) {
            return null;
        }
    }
}
