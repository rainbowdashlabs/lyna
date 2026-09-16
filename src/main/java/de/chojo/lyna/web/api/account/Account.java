package de.chojo.lyna.web.api.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.data.access.AccountLicenses;
import de.chojo.lyna.data.access.AccountSessions;
import de.chojo.lyna.data.access.EmailVerificationTokens;
import de.chojo.lyna.data.access.InstanceSettingsAccess;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.access.DownloadLog;
import de.chojo.lyna.data.access.RevokedJtis;
import de.chojo.lyna.data.dao.account.AccountLicense;
import de.chojo.lyna.data.dao.InstanceSettings;
import de.chojo.lyna.data.dao.account.AccountSession;
import de.chojo.lyna.data.dao.account.AccountIdentity;
import de.chojo.lyna.data.dao.account.DownloadLogEntry;
import de.chojo.jdautil.configuration.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
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
import static org.slf4j.LoggerFactory.getLogger;

public class Account {
    private static final Logger log = getLogger(Account.class);

    private final Auth auth;
    private final Accounts accounts;
    private final AccountLicenses licenses;
    private final InstanceSettingsAccess instanceSettings;
    private final MailingService mailingService;
    private final EmailVerificationTokens emailTokens;
    private final Configuration<ConfigFile> configuration;
    private final AccountSessions sessions;
    private final RevokedJtis revokedJtis;
    private final DownloadLog downloadLog;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public Account(Auth auth,
                   Accounts accounts,
                   AccountLicenses licenses,
                   InstanceSettingsAccess instanceSettings,
                   MailingService mailingService,
                   EmailVerificationTokens emailTokens,
                   Configuration<ConfigFile> configuration,
                   AccountSessions sessions,
                   RevokedJtis revokedJtis,
                   DownloadLog downloadLog,
                   PasswordHasher passwordHasher,
                   JwtService jwtService) {
        this.auth = auth;
        this.accounts = accounts;
        this.licenses = licenses;
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
            path("email", () -> {
                post("change", this::changeEmail);
                post("resend-verification", this::resendVerification);
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
                delete("{id}/sharees/{discordId}", this::removeSharee);
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
        var acc = accounts.findById(session.get().accountId());
        if (acc.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        var link = accounts.findLinkByAccountId(acc.get().id());
        List<AccountSession> active = sessions.activeForAccount(acc.get().id());
        var recent = downloadLog.recentForAccount(acc.get().id(), 5);
        ctx.json(new Overview(
                new AccountInfo(
                        acc.get().id(),
                        acc.get().email(),
                        acc.get().emailVerified(),
                        emailTokens.pendingEmail(acc.get().id()).orElse(null),
                        acc.get().hasPassword(),
                        link.map(AccountIdentity::externalId).orElse(null),
                        link.map(AccountIdentity::linkedAt).orElse(null),
                        acc.get().theme(),
                        acc.get().feel(),
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
        var acc = accounts.findById(session.get().accountId());
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
        accounts.setPasswordHash(acc.get().id(), passwordHasher.hash(body.newPassword()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listSessions(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        List<AccountSession> active = sessions.activeForAccount(session.get().accountId());
        String currentJti = session.get().jti();
        ctx.json(active.stream()
                .map(s -> new SessionInfo(s.jti(), s.issuedAt(), s.lastSeenAt(), s.userAgent(), s.jti().equals(currentJti)))
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
        accounts.unlink(session.get().accountId());
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
        var acc = accounts.findById(session.get().accountId());
        if (acc.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        if (acc.get().email() != null && !acc.get().email().equalsIgnoreCase(body.confirmEmail())) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Confirmation email does not match");
            return;
        }
        sessions.deleteAllForAccount(acc.get().id());
        accounts.delete(acc.get().id());
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
        ctx.json(new DownloadPage(rows, total, page, pageSize,
                downloadLog.productsForAccount(accountId)));
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
            return raw.length() == 10 ? LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.parse(raw);
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
    private void changeEmail(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        EmailChange body;
        try {
            body = json.readValue(ctx.body(), EmailChange.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Malformed request");
            return;
        }
        String email = body == null || body.newEmail() == null ? "" : body.newEmail().trim();
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            ctx.status(HttpStatus.BAD_REQUEST).result("That is not an email address");
            return;
        }
        var taken = accounts.findByEmail(email);
        if (taken.isPresent() && taken.get().id() != session.get().accountId()) {
            // Answered as though it had been sent. Saying "that address is taken" to somebody who is
            // not its owner tells them who has an account here.
            ctx.status(HttpStatus.ACCEPTED);
            return;
        }
        sendVerification(session.get().accountId(), email);
        ctx.status(HttpStatus.ACCEPTED);
    }

    /**
     * Sends the confirmation again, for an address somebody never received it for.
     */
    private void resendVerification(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        var acc = accounts.findById(session.get().accountId());
        if (acc.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        String pending = emailTokens.pendingEmail(acc.get().id()).orElse(acc.get().email());
        if (pending == null || acc.get().emailVerified() && emailTokens.pendingEmail(acc.get().id()).isEmpty()) {
            ctx.status(HttpStatus.ACCEPTED);
            return;
        }
        sendVerification(acc.get().id(), pending);
        ctx.status(HttpStatus.ACCEPTED);
    }

    /**
     * Issues a token for the address and mails the link.
     *
     * <p>Best effort on the sending: the token is written whatever the mail server does, so a resend
     * reaches the same address rather than starting again.
     */
    private void sendVerification(int accountId, String email) {
        var issued = emailTokens.issue(accountId, email, Instant.now().plus(Duration.ofDays(1)));
        String link = configuration.config().links().frontend() + "/verify-email?token=" + issued.token();
        try {
            var renderer = mailingService.renderer();
            var values = java.util.Map.<String, Object>of(
                    "url", link,
                    "senderName", "Lyna",
                    "baseUrl", configuration.config().links().frontend());
            mailingService.send(email, renderer.subject("verify-email", "en", values),
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
        var acc = accounts.findById(session.get().accountId());
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

        String feel = acc.get().feel();
        if (policy.allowUserFeel() && !policy.lockFeel() && body.feel() != null) {
            feel = body.feel().isBlank() ? null : body.feel();
        }

        String darkMode = body.darkMode() == null
                ? acc.get().darkMode()
                : body.darkMode().isBlank() ? null : body.darkMode();

        accounts.setAppearance(acc.get().id(), theme, feel, darkMode);
        ctx.json(new Appearance(theme, feel, darkMode));
    }

    private void listLicenses(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        int accountId = session.get().accountId();
        ctx.json(new LicenseList(
                licenses.owned(accountId).stream().map(Account::toView).toList(),
                licenses.shared(accountId).stream().map(Account::toView).toList()));
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
        List<String> sharees = license.get().role() == AccountLicense.Role.OWNER
                ? licenses.sharees(licenseId).stream().map(Object::toString).toList()
                : List.of();
        ctx.json(new LicenseDetail(
                toView(license.get()),
                licenses.keyForHolder(licenseId, session.get().accountId()).orElse(null),
                sharees,
                downloadLog.recentForLicense(licenseId, license.get().role() == AccountLicense.Role.OWNER
                        ? null
                        : session.get().accountId(), 10)));
    }

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
        Long subject = parseDiscordId(body == null ? null : body.subject());
        if (subject == null) {
            ctx.status(HttpStatus.NOT_FOUND).result("That is not a Discord id");
            return;
        }
        if (Accounts.accountIdForDiscord(subject) == owned.ownerAccountId()) {
            ctx.status(HttpStatus.CONFLICT).result("The owner already holds this license");
            return;
        }
        if (owned.shareesCap() > 0 && owned.shareesUsed() >= owned.shareesCap()) {
            ctx.status(HttpStatus.CONFLICT).result("Cap reached. Revoke a sharee first.");
            return;
        }
        licenses.addSharee(owned.id(), Accounts.accountIdForDiscord(subject));
        tellSharee("licence-shared", subject, owned);
        ctx.status(HttpStatus.CREATED).json(new ShareeView(Long.toString(subject)));
    }

    private void removeSharee(Context ctx) {
        var owned = requireOwned(ctx);
        if (owned == null) return;
        Long subject = parseDiscordId(ctx.pathParam("discordId"));
        if (subject == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        licenses.removeSharee(owned.id(), Accounts.accountIdForDiscord(subject));
        tellSharee("licence-revoked", subject, owned);
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
    private void tellSharee(String template, long shareeDiscordId, AccountLicense license) {
        try {
            var sharee = accounts.findByDiscordId(shareeDiscordId);
            if (sharee.isEmpty() || sharee.get().email() == null) return;
            var renderer = mailingService.renderer();
            var values = java.util.Map.<String, Object>of(
                    "owner", Integer.toString(license.ownerAccountId()),
                    "product", license.productName(),
                    "senderName", "Lyna");
            mailingService.send(sharee.get().email(),
                    renderer.subject(template, "en", values),
                    renderer.render(template, "en", values));
        } catch (Exception e) {
            log.warn("Could not tell {} about the licence for {}", shareeDiscordId, license.productName(), e);
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

    /**
     * @return the Discord id, or null when the text is not one
     */
    private static Long parseDiscordId(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (!trimmed.matches("\\d{5,20}")) return null;
        return Long.parseLong(trimmed);
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

    public record AccountInfo(int id, String email, boolean emailVerified, String pendingEmail,
                              boolean hasPassword, String discordId, Instant discordLinkedAt,
                              String theme, String feel, String darkMode) {
    }

    public record Overview(AccountInfo account, int activeSessions, Instant lastSignInAt, List<DownloadLogEntry> recentDownloads) {
    }

    public record SessionInfo(String jti, Instant issuedAt, Instant lastSeenAt, String userAgent, boolean current) {
    }

    public record ChangePassword(String currentPassword, String newPassword) {
    }

    public record Confirm(String confirmEmail) {
    }

    public record LicenseView(int id, String guildId, int productId, String productName, String productUrl,
                              String userIdentifier, List<String> releaseTypes, String role, String ownerAccountId,
                              int shareesUsed, int shareesCap) {
    }

    public record LicenseList(List<LicenseView> owned, List<LicenseView> shared) {
    }

    public record LicenseDetail(LicenseView license, String key, List<String> sharees,
                                List<DownloadLogEntry> recentDownloads) {
    }

    public record DownloadPage(List<DownloadLogEntry> rows, int totalRows, int page, int pageSize,
                               List<DownloadLog.ProductOption> products) {
    }

    public record EmailChange(String newEmail) {
    }

    public record Appearance(String theme, String feel, String darkMode) {
    }

    public record Sharee(String subject) {
    }

    public record ShareeView(String discordId) {
    }
}
