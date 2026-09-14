package de.chojo.lyna.web.api.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.data.access.AccountLicenses;
import de.chojo.lyna.data.access.AccountSessions;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.access.DownloadLog;
import de.chojo.lyna.data.access.RevokedJtis;
import de.chojo.lyna.data.dao.account.AccountLicense;
import de.chojo.lyna.data.dao.account.AccountSession;
import de.chojo.lyna.data.dao.account.DiscordLink;
import de.chojo.lyna.data.dao.account.DownloadLogEntry;
import de.chojo.lyna.web.api.auth.Auth;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static org.slf4j.LoggerFactory.getLogger;

public class Account {
    private static final Logger log = getLogger(Account.class);

    private final Auth auth;
    private final Accounts accounts;
    private final AccountLicenses licenses;
    private final AccountSessions sessions;
    private final RevokedJtis revokedJtis;
    private final DownloadLog downloadLog;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public Account(Auth auth,
                   Accounts accounts,
                   AccountLicenses licenses,
                   AccountSessions sessions,
                   RevokedJtis revokedJtis,
                   DownloadLog downloadLog,
                   PasswordHasher passwordHasher,
                   JwtService jwtService) {
        this.auth = auth;
        this.accounts = accounts;
        this.licenses = licenses;
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
                        acc.get().hasPassword(),
                        link.map(l -> Long.toString(l.discordUserId())).orElse(null),
                        link.map(DiscordLink::linkedAt).orElse(null),
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

    private void listDownloads(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        int limit;
        try {
            limit = Math.min(Math.max(Integer.parseInt(ctx.queryParamAsClass("limit", String.class).getOrDefault("25")), 1), 200);
        } catch (NumberFormatException e) {
            limit = 25;
        }
        List<DownloadLogEntry> entries = downloadLog.recentForAccount(session.get().accountId(), limit);
        ctx.json(entries);
    }

    /**
     * The Discord id the calling account is linked to.
     *
     * <p>Licenses are keyed by that id, not by the account, so an account that has never linked one
     * holds nothing - which is a legitimate answer rather than a refusal.
     */
    private Optional<Long> linkedDiscordId(int accountId) {
        return accounts.findLinkByAccountId(accountId).map(DiscordLink::discordUserId);
    }

    private void listLicenses(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        var discordId = linkedDiscordId(session.get().accountId());
        if (discordId.isEmpty()) {
            ctx.json(new LicenseList(List.of(), List.of()));
            return;
        }
        ctx.json(new LicenseList(
                licenses.owned(discordId.get()).stream().map(Account::toView).toList(),
                licenses.shared(discordId.get()).stream().map(Account::toView).toList()));
    }

    private void licenseDetail(Context ctx) {
        var session = require(ctx);
        if (session.isEmpty()) return;
        Integer licenseId = pathId(ctx, "id");
        if (licenseId == null) return;
        var discordId = linkedDiscordId(session.get().accountId());
        if (discordId.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        var license = licenses.forHolder(licenseId, discordId.get());
        if (license.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        List<String> sharees = license.get().role() == AccountLicense.Role.OWNER
                ? licenses.sharees(licenseId).stream().map(Object::toString).toList()
                : List.of();
        ctx.json(new LicenseDetail(
                toView(license.get()),
                licenses.keyForHolder(licenseId, discordId.get()).orElse(null),
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
        if (subject == owned.ownerDiscordId()) {
            ctx.status(HttpStatus.CONFLICT).result("The owner already holds this license");
            return;
        }
        if (owned.shareesCap() > 0 && owned.shareesUsed() >= owned.shareesCap()) {
            ctx.status(HttpStatus.CONFLICT).result("Cap reached. Revoke a sharee first.");
            return;
        }
        licenses.addSharee(owned.id(), subject);
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
        licenses.removeSharee(owned.id(), subject);
        ctx.status(HttpStatus.NO_CONTENT);
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
        var discordId = linkedDiscordId(session.get().accountId());
        if (discordId.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
        var license = licenses.forHolder(licenseId, discordId.get());
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
                Long.toString(license.ownerDiscordId()),
                license.shareesUsed(),
                license.shareesCap());
    }

    public record AccountInfo(int id, String email, boolean hasPassword, String discordId, Instant discordLinkedAt,
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
                              String userIdentifier, List<String> releaseTypes, String role, String ownerDiscordId,
                              int shareesUsed, int shareesCap) {
    }

    public record LicenseList(List<LicenseView> owned, List<LicenseView> shared) {
    }

    public record LicenseDetail(LicenseView license, String key, List<String> sharees,
                                List<DownloadLogEntry> recentDownloads) {
    }

    public record Sharee(String subject) {
    }

    public record ShareeView(String discordId) {
    }
}
