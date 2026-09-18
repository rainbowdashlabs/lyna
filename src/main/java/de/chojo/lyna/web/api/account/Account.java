/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.entity.AccountLicense;
import de.chojo.lyna.feature.account.entity.AccountSession;
import de.chojo.lyna.feature.account.repository.AccountEmailRepository;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.repository.AccountSessionRepository;
import de.chojo.lyna.feature.account.repository.EmailVerificationTokenRepository;
import de.chojo.lyna.feature.account.repository.RevokedJtiRepository;
import de.chojo.lyna.feature.account.service.AccountLinkService;
import de.chojo.lyna.feature.account.service.AccountService;
import de.chojo.lyna.feature.account.service.UsernameService;
import de.chojo.lyna.feature.download.entity.DownloadLogEntry;
import de.chojo.lyna.feature.download.repository.DownloadLogRepository;
import de.chojo.lyna.feature.instance.entity.InstanceSettings;
import de.chojo.lyna.feature.instance.repository.InstanceSettingsRepository;
import de.chojo.lyna.feature.license.repository.LicenseInviteRepository;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.web.api.auth.Auth;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.patch;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;
import static org.slf4j.LoggerFactory.getLogger;

public class Account {
    private static final Logger log = getLogger(Account.class);

    private final Auth auth;
    private final AccountService accountService;
    private final UsernameService usernameService;
    private final AccountLinkService accountLinkService;
    private final AccountLicenseRepository licenses;
    private final AccountEmailRepository accountEmails;
    private final LicenseInviteRepository invites;
    private final InstanceSettingsRepository instanceSettings;
    private final MailingService mailingService;
    private final EmailVerificationTokenRepository emailTokens;
    private final Conf configuration;
    private final AccountSessionRepository sessions;
    private final RevokedJtiRepository revokedJtis;
    private final DownloadLogRepository downloadLog;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Inject
    public Account(
            Auth auth,
            AccountService accountService,
            UsernameService usernameService,
            AccountLinkService accountLinkService,
            AccountLicenseRepository licenses,
            AccountEmailRepository accountEmails,
            LicenseInviteRepository invites,
            InstanceSettingsRepository instanceSettings,
            MailingService mailingService,
            EmailVerificationTokenRepository emailTokens,
            Conf configuration,
            AccountSessionRepository sessions,
            RevokedJtiRepository revokedJtis,
            DownloadLogRepository downloadLog,
            PasswordHasher passwordHasher,
            JwtService jwtService) {
        this.auth = auth;
        this.accountService = accountService;
        this.usernameService = usernameService;
        this.accountLinkService = accountLinkService;
        this.licenses = licenses;
        this.accountEmails = accountEmails;
        this.invites = invites;
        this.instanceSettings = instanceSettings;
        this.mailingService = mailingService;
        this.emailTokens = emailTokens;
        this.configuration = configuration;
        this.sessions = sessions;
        this.revokedJtis = revokedJtis;
        this.downloadLog = downloadLog;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
    }

    public void init() {
        path("account", () -> {
            get(this::overview);
            delete(this::deleteAccount);
            post("password", this::changePassword);
            patch("appearance", this::updateAppearance);
            put("username", this::setUsername);
            path("emails", () -> {
                get(this::listEmails);
                post(this::addEmail);
                post("{address}/primary", this::makeEmailPrimary);
                delete("{address}", this::removeEmail);
            });
            get("sessions", this::listSessions);
            delete("sessions", this::endOtherSessions);
            delete("sessions/{jti}", this::revokeSession);
            path("discord", () -> {
                delete(this::unlinkDiscord);
            });
            get("downloads", this::listDownloads);
            path("licenses", () -> {
                get(this::listLicenses);
                get("{id}", this::licenseDetail);
                post("{id}/sharees", this::addSharee);
                delete("{id}/sharees/{ref}", this::removeSharee);
            });
        });
    }

    private Optional<JwtService.Verified> require(Context ctx) {
        Optional<JwtService.Verified> session = auth.currentSession(ctx);
        if (session.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
        }
        return session;
    }

    private void overview(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        var acc = accountService.findById(session.get().accountId());
        if (acc.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        var link = accountLinkService.discordIdentity(acc.get().id());
        List<AccountSession> active = sessions.activeForAccount(acc.get().id());
        var recent = downloadLog.recentForAccount(acc.get().id(), 5);
        ctx.json(new Overview(
                new AccountInfo(
                        acc.get().id(),
                        acc.get().email(),
                        acc.get().emailVerified(),
                        acc.get().hasPassword(),
                        link.map(AccountIdentity::externalId).orElse(null),
                        link.map(AccountIdentity::linkedAt).orElse(null),
                        acc.get().displayName(),
                        link.isEmpty(),
                        acc.get().theme(),
                        acc.get().darkMode()),
                active.size(),
                acc.get().lastLoginAt(),
                recent));
    }

    private void changePassword(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        ChangePassword body;
        try {
            body = json.readValue(ctx.body(), ChangePassword.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        if (body.newPassword() == null || body.newPassword().length() < 8) {
            ctx.status(HttpStatus.BAD_REQUEST).result("New password must be at least 8 characters");
            return;
        }
        var acc = accountService.findById(session.get().accountId());
        if (acc.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        if (acc.get().hasPassword()) {
            if (body.currentPassword() == null
                    || !passwordHasher.verify(body.currentPassword(), acc.get().passwordHash())) {
                ctx.status(HttpStatus.UNAUTHORIZED).result("Current password is incorrect");
                return;
            }
        }
        accountService.setPasswordHash(acc.get().id(), passwordHasher.hash(body.newPassword()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listSessions(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        List<AccountSession> active = sessions.activeForAccount(session.get().accountId());
        String currentJti = session.get().jti();
        ctx.json(active.stream()
                .map(s -> new SessionInfo(
                        s.jti(),
                        s.issuedAt(),
                        s.lastSeenAt(),
                        s.userAgent(),
                        s.jti().equals(currentJti)))
                .toList());
    }

    private void revokeSession(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        String jti = ctx.pathParam("jti");
        var entry = sessions.find(jti);
        if (entry.isEmpty() || entry.get().accountId() != session.get().accountId()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        revokedJtis.revoke(jti, entry.get().expiresAt());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void endOtherSessions(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        List<AccountSession> active = sessions.activeForAccount(session.get().accountId());
        String currentJti = session.get().jti();
        for (var s : active) {
            if (!s.jti().equals(currentJti)) {
                revokedJtis.revoke(s.jti(), s.expiresAt());
            }
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void unlinkDiscord(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        accountLinkService.unlink(session.get().accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void deleteAccount(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        Confirm body;
        try {
            body = json.readValue(ctx.body(), Confirm.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        var acc = accountService.findById(session.get().accountId());
        if (acc.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        if (acc.get().email() != null && !acc.get().email().equalsIgnoreCase(body.confirmEmail())) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Confirmation email does not match");
            return;
        }
        sessions.deleteAllForAccount(acc.get().id());
        accountService.delete(acc.get().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * The account's downloads, narrowed by whatever the query names.
     *
     * <p>A license filter is honoured only for that license's owner. A sharee asking about the
     * whole license is quietly narrowed to their own rows rather than refused, which tells them
     * nothing either way about what the license has seen.
     */
    private void listDownloads(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        int accountId = session.get().accountId();
        int pageSize = intParam(ctx, "pageSize", 25, 1, 200);
        int page = Math.max(intParam(ctx, "page", 1, 1, Integer.MAX_VALUE), 1);

        Integer licenseId = filterId(ctx, "license");
        Integer scopedAccount = accountId;
        if (licenseId != null) {
            boolean owns = licenses.forHolder(licenseId, accountId)
                    .filter(license -> license.role() == AccountLicense.Role.OWNER)
                    .isPresent();
            if (owns) scopedAccount = null;
        }

        Instant from = instantParam(ctx, "from");
        Instant to = instantParam(ctx, "to");
        Integer productId = filterId(ctx, "product");
        String source = ctx.queryParam("source");

        List<DownloadLogEntry> rows = downloadLog.page(
                scopedAccount, licenseId, productId, source, from, to, pageSize, (page - 1) * pageSize);
        int total = downloadLog.count(scopedAccount, licenseId, productId, source, from, to);
        ctx.json(new DownloadPage(rows, total, page, pageSize, downloadLog.productsForAccount(accountId)));
    }

    /**
     * @return the query parameter as a number within the bounds, or the fallback when it is neither
     */
    private static int intParam(Context ctx, String name, int fallback, int min, int max) {
        String raw = ctx.queryParam(name);
        if (raw == null) return fallback;
        try {
            return Math.min(Math.max(Integer.parseInt(raw), min), max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * @return the query parameter as an id, or null when it is absent or not one
     */
    private static Integer filterId(Context ctx, String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return the query parameter as a moment, or null when it is absent or unreadable
     */
    private static Instant instantParam(Context ctx, String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) return null;
        try {
            return raw.length() == 10
                    ? LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant()
                    : Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Starts confirming an address.
     *
     * <p>The account keeps whatever address it has until the link is followed. That is the whole
     * point of the step: typing an address here proves nothing about being able to read it, so
     * nothing is taken away from the old one until something does.
     */
    private void listEmails(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        ctx.json(accountEmails.of(session.get().accountId()).stream()
                .map(e -> new EmailView(e.email(), e.verified(), e.primary()))
                .toList());
    }

    /**
     * Claims another address and sends the link that would prove it.
     *
     * <p>Answered the same way whether or not somebody else holds it: saying "that one is taken" to
     * a person who is not its owner tells them who has an account here.
     */
    private void addEmail(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        NewEmail body;
        try {
            body = json.readValue(ctx.body(), NewEmail.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Malformed request");
            return;
        }
        String email =
                body == null || body.address() == null ? "" : body.address().trim();
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            ctx.status(HttpStatus.BAD_REQUEST).result("That is not an email address");
            return;
        }
        try {
            accountEmails.add(session.get().accountId(), email);
        } catch (IllegalStateException e) {
            ctx.status(HttpStatus.ACCEPTED);
            return;
        }
        sendVerification(session.get().accountId(), email);
        ctx.status(HttpStatus.ACCEPTED);
    }

    private void makeEmailPrimary(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        if (!accountEmails.makePrimary(session.get().accountId(), ctx.pathParam("address"))) {
            ctx.status(HttpStatus.CONFLICT).result("Confirm that address before writing to it");
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void removeEmail(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        if (!accountEmails.remove(session.get().accountId(), ctx.pathParam("address"))) {
            ctx.status(HttpStatus.CONFLICT)
                    .result("That is the address this account is written to. Make another one primary first.");
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Issues a token for the address and mails the link.
     *
     * <p>Best effort on the sending: the token is written whatever the mail server does, so a resend
     * reaches the same address rather than starting again.
     */
    private void sendVerification(int accountId, String email) {
        var issued = emailTokens.issue(accountId, email, Instant.now().plus(Duration.ofDays(1)));
        String link = configuration.main().links().frontend() + "/verify-email?token=" + issued.token();
        try {
            var renderer = mailingService.renderer();
            var values = java.util.Map.<String, Object>of("url", link);
            mailingService.send(
                    email,
                    renderer.subject("verify-email", "en", values),
                    renderer.render("verify-email", "en", values));
        } catch (Exception e) {
            log.warn("Could not send the verification mail for account {}", accountId, e);
        }
    }

    /**
     * Stores what the account chose about how the application looks.
     *
     * <p>The operator's policy is applied here rather than only in the page: a theme the operator
     * did not allow, or a choice they locked, is dropped whatever the request says. The page uses
     * the same rules to grey the controls out, but a request that gets past them changes nothing.
     */
    private void updateAppearance(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        Appearance body;
        try {
            body = json.readValue(ctx.body(), Appearance.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Malformed request");
            return;
        }
        var acc = accountService.findById(session.get().accountId());
        if (acc.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        InstanceSettings policy = instanceSettings.get();

        String theme = acc.get().theme();
        if (policy.allowUserTheme() && body.theme() != null) {
            if (!policy.enabledThemes().isEmpty() && !policy.enabledThemes().contains(body.theme())) {
                ctx.status(HttpStatus.BAD_REQUEST).result("That theme is not available here");
                return;
            }
            theme = body.theme().isBlank() ? null : body.theme();
        }

        String darkMode =
                body.darkMode() == null ? acc.get().darkMode() : body.darkMode().isBlank() ? null : body.darkMode();

        accountService.setAppearance(acc.get().id(), theme, darkMode);
        ctx.json(new Appearance(theme, darkMode));
    }

    private void listLicenses(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        int accountId = session.get().accountId();
        ctx.json(new LicenseList(
                licenses.owned(accountId).stream().map(Account::toView).toList(),
                licenses.shared(accountId).stream().map(Account::toView).toList()));
    }

    /**
     * Lets somebody choose what they are called.
     *
     * <p>Refused while Discord supplies the name: two writers on one field means the next sign-in
     * silently undoes whatever was typed here, and the page says so rather than letting it happen.
     */
    private void setUsername(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        Username body;
        try {
            body = json.readValue(ctx.body(), Username.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Malformed request");
            return;
        }
        try {
            usernameService.setUsername(session.get().accountId(), body == null ? null : body.username());
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result(e.getMessage());
            return;
        } catch (IllegalStateException e) {
            ctx.status(HttpStatus.CONFLICT).result(e.getMessage());
            return;
        }
        accountService
                .findById(session.get().accountId())
                .ifPresent(account -> ctx.json(new Username(account.displayName())));
    }

    private void licenseDetail(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        Integer licenseId = pathId(ctx, "id");
        if (licenseId == null) return;
        var license = licenses.forHolder(licenseId, session.get().accountId());
        if (license.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        List<ShareeView> sharees = license.get().role() == AccountLicense.Role.OWNER ? shareesOf(licenseId) : List.of();
        ctx.json(new LicenseDetail(
                toView(license.get()),
                licenses.keyForHolder(licenseId, session.get().accountId()).orElse(null),
                sharees,
                downloadLog.recentForLicense(
                        licenseId,
                        license.get().role() == AccountLicense.Role.OWNER
                                ? null
                                : session.get().accountId(),
                        10)));
    }

    /**
     * The people a licence is shared with, named rather than numbered.
     *
     * <p>An account that has never been named shows as its opaque ref, which is the only thing about
     * it that is safe to print. Addresses appear only for invites, which the owner typed themselves.
     */
    private List<ShareeView> shareesOf(int licenseId) {
        List<ShareeView> views = new java.util.ArrayList<>();
        for (int shareeId : licenses.sharees(licenseId)) {
            String name = accountService
                    .findById(shareeId)
                    .map(de.chojo.lyna.feature.account.entity.Account::displayName)
                    .orElse(null);
            views.add(new ShareeView("a" + shareeId, name == null ? "a" + shareeId : name, false));
        }
        for (var invite : invites.standing(licenseId)) {
            views.add(new ShareeView("e" + invite.email(), invite.email(), true));
        }
        return views;
    }

    /**
     * Shares a licence with somebody named by username, or invites an address.
     *
     * <p>An address that already has an account is shared with straight away; one that does not is
     * invited and waits for that address to be proved. Either way the owner is told which happened,
     * because "shared" and "invited" are different promises.
     */
    private void addSharee(Context ctx) {
        var owned = requireOwned(ctx);
        if (owned == null) return;
        Sharee body;
        try {
            body = json.readValue(ctx.body(), Sharee.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Malformed request");
            return;
        }
        String subject =
                body == null || body.subject() == null ? "" : body.subject().trim();
        if (subject.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Name somebody by username, or give an address");
            return;
        }
        if (owned.shareesCap() > 0 && owned.shareesUsed() >= owned.shareesCap()) {
            ctx.status(HttpStatus.CONFLICT).result("Cap reached. Revoke a sharee first.");
            return;
        }

        Optional<de.chojo.lyna.feature.account.entity.Account> target =
                subject.contains("@") ? accountService.findByEmail(subject) : accountService.findByUsername(subject);

        if (target.isEmpty() && !subject.contains("@")) {
            ctx.status(HttpStatus.NOT_FOUND).result("Nobody here goes by that name");
            return;
        }
        if (target.isEmpty()) {
            invites.invite(owned.id(), subject);
            ctx.status(HttpStatus.CREATED).json(new ShareeView("e" + subject, subject, true));
            return;
        }
        int shareeId = target.get().id();
        if (shareeId == owned.ownerAccountId()) {
            ctx.status(HttpStatus.CONFLICT).result("The owner already holds this license");
            return;
        }
        if (!licenses.addSharee(owned.id(), shareeId)) {
            ctx.status(HttpStatus.CONFLICT).result("They already hold this license");
            return;
        }
        tellSharee("licence-shared", target.get(), owned);
        String name = target.get().displayName();
        ctx.status(HttpStatus.CREATED)
                .json(new ShareeView("a" + shareeId, name == null ? "a" + shareeId : name, false));
    }

    /**
     * Takes a share back, whether it was accepted or is still an invite waiting to be.
     *
     * <p>The ref says which: {@code a} for an account, {@code e} for an invited address. The owner
     * never handles an account id or a snowflake, so nothing about one sharee leaks through the page
     * another one is looking at.
     */
    private void removeSharee(Context ctx) {
        var owned = requireOwned(ctx);
        if (owned == null) return;
        String ref = ctx.pathParam("ref");
        if (ref.length() < 2) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        String rest = ref.substring(1);
        if (ref.charAt(0) == 'e') {
            invites.withdraw(owned.id(), rest);
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }
        int shareeId;
        try {
            shareeId = Integer.parseInt(rest);
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        var sharee = accountService.findById(shareeId);
        licenses.removeSharee(owned.id(), shareeId);
        sharee.ifPresent(account -> tellSharee("licence-revoked", account, owned));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Tells somebody a licence was shared with them, or taken back.
     *
     * <p>Only reaches an id that has linked an account and given it an address - a Discord id on its
     * own is not somewhere a mail can go. The owner is named by their Discord id, which is what the
     * licences page already shows; their email is theirs and is never passed on.
     *
     * <p>Best effort on purpose. The share is a database row and has already been written; a mail
     * server that will not take the message is not a reason to tell the caller their share failed.
     */
    private void tellSharee(
            String template, de.chojo.lyna.feature.account.entity.Account sharee, AccountLicense license) {
        try {
            if (sharee.email() == null) return;
            String owner = accountService
                    .findById(license.ownerAccountId())
                    .map(de.chojo.lyna.feature.account.entity.Account::displayName)
                    .orElse("the owner");
            var renderer = mailingService.renderer();
            var values = java.util.Map.<String, Object>of(
                    "owner", owner == null ? "the owner" : owner, "product", license.productName());
            mailingService.send(
                    sharee.email(), renderer.subject(template, "en", values), renderer.render(template, "en", values));
        } catch (Exception e) {
            log.warn("Could not tell account {} about the licence for {}", sharee.id(), license.productName(), e);
        }
    }

    /**
     * The license named in the path, but only when the caller owns it.
     *
     * <p>A sharee is told the same thing a stranger is: the sharee controls are the owner's, and
     * saying "yours, but not for this" would tell them which licenses exist.
     *
     * @return the license, or null when the response has already been written
     */
    private AccountLicense requireOwned(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return null;
        Integer licenseId = pathId(ctx, "id");
        if (licenseId == null) return null;
        var license = licenses.forHolder(licenseId, session.get().accountId());
        if (license.isEmpty() || license.get().role() != AccountLicense.Role.OWNER) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
        return license.get();
    }

    /**
     * @return the path parameter as a number, or null when it is not one and the response says so
     */
    private static Integer pathId(Context ctx, String name) {
        try {
            return Integer.parseInt(ctx.pathParam(name));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
    }

    private static LicenseView toView(AccountLicense license) {
        return new LicenseView(
                license.id(),
                Long.toString(license.guildId()),
                license.productId(),
                license.productName(),
                license.productUrl(),
                license.userIdentifier(),
                license.releaseTypes(),
                license.role().name().toLowerCase(),
                Integer.toString(license.ownerAccountId()),
                license.shareesUsed(),
                license.shareesCap());
    }

    /**
     * @param verified whether a link sent to it was followed. Everything an address is good for hangs
     *                 off this.
     */
    public record EmailView(String address, boolean verified, boolean primary) {}

    public record NewEmail(String address) {}

    /**
     * @param username    the name as it is shown, digits and all
     * @param nameIsTheirs whether this account may change its own name, which it may not while a
     *                     provider is the one supplying it
     */
    public record AccountInfo(
            int id,
            String email,
            boolean emailVerified,
            boolean hasPassword,
            String discordId,
            Instant discordLinkedAt,
            String username,
            boolean nameIsTheirs,
            String theme,
            String darkMode) {}

    public record Username(String username) {}

    public record Overview(
            AccountInfo account, int activeSessions, Instant lastSignInAt, List<DownloadLogEntry> recentDownloads) {}

    public record SessionInfo(String jti, Instant issuedAt, Instant lastSeenAt, String userAgent, boolean current) {}

    public record ChangePassword(String currentPassword, String newPassword) {}

    public record Confirm(String confirmEmail) {}

    public record LicenseView(
            int id,
            String guildId,
            int productId,
            String productName,
            String productUrl,
            String userIdentifier,
            List<String> releaseTypes,
            String role,
            String ownerAccountId,
            int shareesUsed,
            int shareesCap) {}

    public record LicenseList(List<LicenseView> owned, List<LicenseView> shared) {}

    public record LicenseDetail(
            LicenseView license, String key, List<ShareeView> sharees, List<DownloadLogEntry> recentDownloads) {}

    public record DownloadPage(
            List<DownloadLogEntry> rows,
            int totalRows,
            int page,
            int pageSize,
            List<DownloadLogRepository.ProductOption> products) {}

    public record Appearance(String theme, String darkMode) {}

    /**
     * @param subject a username, with or without its digits, or an email address
     */
    public record Sharee(String subject) {}

    /**
     * @param ref     how to address this sharee when revoking, opaque to the page
     * @param name    what to show: a username, or the address of an invite the owner typed
     * @param pending whether this is an invite still waiting to be answered
     */
    public record ShareeView(String ref, String name, boolean pending) {}
}
