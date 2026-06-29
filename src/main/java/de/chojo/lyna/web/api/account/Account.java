package de.chojo.lyna.web.api.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.data.access.AccountSessions;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.access.DownloadLog;
import de.chojo.lyna.data.access.RevokedJtis;
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
    private final AccountSessions sessions;
    private final RevokedJtis revokedJtis;
    private final DownloadLog downloadLog;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public Account(Auth auth,
                   Accounts accounts,
                   AccountSessions sessions,
                   RevokedJtis revokedJtis,
                   DownloadLog downloadLog,
                   PasswordHasher passwordHasher,
                   JwtService jwtService) {
        this.auth = auth;
        this.accounts = accounts;
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
}
