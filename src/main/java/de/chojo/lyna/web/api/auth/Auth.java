/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.auth.DiscordOAuthClient;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.elements.discord.OAuth;
import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.repository.AccountSessionRepository;
import de.chojo.lyna.feature.account.repository.EmailVerificationTokenRepository;
import de.chojo.lyna.feature.account.repository.PasswordResetTokenRepository;
import de.chojo.lyna.feature.account.repository.RevokedJtiRepository;
import de.chojo.lyna.feature.account.service.AccountEmailService;
import de.chojo.lyna.feature.account.service.AccountLinkService;
import de.chojo.lyna.feature.account.service.AccountService;
import de.chojo.lyna.mail.MailingService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static org.slf4j.LoggerFactory.getLogger;

public class Auth {
    private static final Logger log = getLogger(Auth.class);
    private static final String STATE_COOKIE = "lyna_oauth_state";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Conf configuration;
    private final AccountLinkService accountLinkService;
    private final AccountService accountService;
    private final AccountEmailService accountEmails;
    private final AccountSessionRepository accountSessions;
    private final RevokedJtiRepository revokedJtis;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final EmailVerificationTokenRepository emailTokens;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final DiscordOAuthClient oauthClient;
    private final OAuth oauthConfig;
    private final MailingService mailingService;
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Inject
    public Auth(
            Conf configuration,
            AccountLinkService accountLinkService,
            AccountService accountService,
            AccountEmailService accountEmails,
            AccountSessionRepository accountSessions,
            RevokedJtiRepository revokedJtis,
            PasswordResetTokenRepository passwordResetTokens,
            EmailVerificationTokenRepository emailTokens,
            PasswordHasher passwordHasher,
            JwtService jwtService,
            DiscordOAuthClient oauthClient,
            OAuth oauthConfig,
            MailingService mailingService) {
        this.configuration = configuration;
        this.accountLinkService = accountLinkService;
        this.accountService = accountService;
        this.accountEmails = accountEmails;
        this.accountSessions = accountSessions;
        this.revokedJtis = revokedJtis;
        this.passwordResetTokens = passwordResetTokens;
        this.emailTokens = emailTokens;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.oauthClient = oauthClient;
        this.oauthConfig = oauthConfig;
        this.mailingService = mailingService;
    }

    public void init() {
        path("auth", () -> {
            post("signup", this::signup);
            post("login", this::login);
            post("logout", this::logout);
            get("me", this::me);
            path("discord", () -> {
                get("start", this::discordStart);
                get("callback", this::discordCallback);
            });
            path("password/reset", () -> {
                post("request", this::passwordResetRequest);
                post("confirm", this::passwordResetConfirm);
            });
            path("email", () -> post("verify", this::verifyEmail));
        });
    }

    private void passwordResetRequest(Context ctx) {
        ResetRequest body;
        try {
            body = json.readValue(ctx.body(), ResetRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        // Always respond NO_CONTENT regardless of whether an account exists, to avoid leaking
        // account presence via the response.
        ctx.status(HttpStatus.NO_CONTENT);
        if (body.email() == null || body.email().isBlank()) return;
        var account = accountService.findByEmail(body.email());
        if (account.isEmpty()) return;
        var issued = passwordResetTokens.issue(
                account.get().id(), java.time.Instant.now().plus(java.time.Duration.ofHours(1)));
        String link = configuration.main().links().frontend() + "/reset-password?token=" + issued.token();
        var renderer = mailingService.renderer();
        var values = java.util.Map.<String, Object>of("url", link);
        try {
            mailingService.send(
                    body.email(),
                    renderer.subject("reset-password", "en", values),
                    renderer.render("reset-password", "en", values));
        } catch (Exception e) {
            log.warn("Failed to send password reset mail", e);
        }
    }

    /**
     * Asks somebody to confirm the address they just signed up with.
     *
     * <p>Best effort, and the account exists either way: somebody who never receives this can ask
     * for it again from their security page, and being unable to send mail is not a reason to refuse
     * a signup.
     */
    private void sendVerification(int accountId, String email) {
        var issued = emailTokens.issue(accountId, email, java.time.Instant.now().plus(java.time.Duration.ofDays(1)));
        String link = configuration.main().links().frontend() + "/verify-email?token=" + issued.token();
        try {
            var values = java.util.Map.<String, Object>of("url", link);
            mailingService.send(
                    email,
                    mailingService.renderer().subject("verify-email", "en", values),
                    mailingService.renderer().render("verify-email", "en", values));
        } catch (Exception e) {
            log.warn("Could not send the verification mail for account {}", accountId, e);
        }
    }

    /**
     * Confirms an address from the link in a mail.
     *
     * <p>Not behind a session: the link is followed from a mailbox, which may not be the browser the
     * account is signed in on - and needing to sign in first would mean needing the address it is
     * about to confirm. The token is the proof; a bad one says so and nothing else.
     */
    private void verifyEmail(Context ctx) {
        VerifyEmail body;
        try {
            body = json.readValue(ctx.body(), VerifyEmail.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Malformed request");
            return;
        }
        if (body == null || body.token() == null || body.token().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Missing token");
            return;
        }
        var confirmed = emailTokens.consume(body.token());
        if (confirmed.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("That link has expired or has already been used");
            return;
        }
        accountEmails.confirm(confirmed.get().accountId(), confirmed.get().email());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record VerifyEmail(String token) {}

    private void passwordResetConfirm(Context ctx) {
        ResetConfirm body;
        try {
            body = json.readValue(ctx.body(), ResetConfirm.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        if (body.token() == null
                || body.newPassword() == null
                || body.newPassword().length() < 8) {
            ctx.status(HttpStatus.BAD_REQUEST).result("token and an 8+ character newPassword are required");
            return;
        }
        var accountId = passwordResetTokens.consume(body.token());
        if (accountId.isEmpty()) {
            ctx.status(HttpStatus.GONE).result("Token is invalid or expired");
            return;
        }
        accountService.setPasswordHash(accountId.get(), passwordHasher.hash(body.newPassword()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record ResetRequest(String email) {}

    public record ResetConfirm(String token, String newPassword) {}

    private void signup(Context ctx) {
        Credentials creds = readCredentials(ctx);
        if (creds == null) return;

        if (accountService.findByEmail(creds.email()).isPresent()) {
            ctx.status(HttpStatus.CONFLICT).result("Email already registered");
            return;
        }
        Account account = accountService.register(creds.email(), passwordHasher.hash(creds.password()));
        sendVerification(account.id(), creds.email());
        issueAndWrite(ctx, account, null, HttpStatus.CREATED);
    }

    private void login(Context ctx) {
        Credentials creds = readCredentials(ctx);
        if (creds == null) return;

        Optional<Account> opt = accountService.findByEmail(creds.email());
        if (opt.isEmpty()
                || !opt.get().hasPassword()
                || !passwordHasher.verify(creds.password(), opt.get().passwordHash())) {
            ctx.status(HttpStatus.UNAUTHORIZED).result("Invalid credentials");
            return;
        }
        Account account = opt.get();
        accountService.touchLastLogin(account.id());
        Long discordId = accountLinkService
                .discordIdentity(account.id())
                .map(AccountIdentity::externalIdAsLong)
                .orElse(null);
        issueAndWrite(ctx, account, discordId, HttpStatus.OK);
    }

    private void logout(Context ctx) {
        Optional<JwtService.Verified> verified = currentSession(ctx);
        if (verified.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        revokedJtis.revoke(verified.get().jti(), verified.get().expiresAt());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void me(Context ctx) {
        Optional<JwtService.Verified> verified = currentSession(ctx);
        if (verified.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        Optional<Account> account = accountService.findById(verified.get().accountId());
        if (account.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        Optional<AccountIdentity> link =
                accountLinkService.discordIdentity(account.get().id());
        ctx.json(toMePayload(account.get(), link.orElse(null)));
    }

    private void discordStart(Context ctx) {
        if (!oauthConfig.configured()) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE).result("Discord sign-in is not configured on this instance.");
            return;
        }
        String state = randomState();
        ctx.cookie(STATE_COOKIE, state, 600);
        ctx.redirect(oauthClient.buildAuthorizeUrl(state));
    }

    /**
     * Gives the account the address Discord signed in with, as a proved one.
     *
     * <p>Only an address Discord says it has verified. An unverified one is a string somebody typed
     * into Discord, and proving it here would hand them the licences bought with it - which is the
     * whole point of a proved address.
     *
     * <p>An address another account already holds is left alone rather than taken, and the sign-in
     * carries on: somebody arriving through Discord should not be refused because an address they
     * also use belongs elsewhere.
     */
    private void attachDiscordEmail(int accountId, DiscordOAuthClient.DiscordUser discordUser) {
        discordUser.provedEmail().ifPresent(email -> {
            try {
                accountEmails.confirm(accountId, email);
            } catch (IllegalStateException e) {
                log.info("Not attaching {} from Discord to account {}: {}", email, accountId, e.getMessage());
            }
        });
    }

    private void discordCallback(Context ctx) {
        if (!oauthConfig.configured()) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE).result("Discord sign-in is not configured on this instance.");
            return;
        }
        String state = ctx.queryParam("state");
        String code = ctx.queryParam("code");
        String cookieState = ctx.cookie(STATE_COOKIE);
        ctx.removeCookie(STATE_COOKIE, "/");

        if (state == null || cookieState == null || !state.equals(cookieState) || code == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid OAuth state");
            return;
        }

        DiscordOAuthClient.DiscordUser discordUser;
        try {
            String accessToken = oauthClient.exchangeCode(code);
            discordUser = oauthClient.fetchUser(accessToken);
        } catch (Exception e) {
            log.warn("Discord OAuth callback failed", e);
            ctx.status(HttpStatus.BAD_GATEWAY).result("Discord OAuth failed");
            return;
        }

        Optional<JwtService.Verified> existing = currentSession(ctx);
        Account account;
        if (existing.isPresent()) {
            Optional<Account> me = accountService.findById(existing.get().accountId());
            if (me.isEmpty()) {
                ctx.status(HttpStatus.UNAUTHORIZED);
                return;
            }
            account = me.get();
            try {
                accountLinkService.link(
                        account.id(), discordUser.id(), AccountIdentity.Verification.OAUTH, discordUser.handle());
            } catch (IllegalStateException e) {
                // Somebody else holds this Discord account. Refusing is the point - moving it would
                // carry their licences across - so say so rather than fail with a server error.
                ctx.redirect("/account/security?linked=taken");
                return;
            }
            attachDiscordEmail(account.id(), discordUser);
        } else {
            account = accountService.findByDiscordId(discordUser.id()).orElseGet(() -> {
                Account created = accountService.register(null, null);
                accountLinkService.link(
                        created.id(), discordUser.id(), AccountIdentity.Verification.OAUTH, discordUser.handle());
                return created;
            });
            accountLinkService.rememberHandle(discordUser.id(), discordUser.handle());
            attachDiscordEmail(account.id(), discordUser);
            accountService.touchLastLogin(account.id());
        }
        JwtService.Issued issued = jwtService.issue(account.id(), discordUser.id());
        accountSessions.record(issued.jti(), account.id(), issued.expiresAt(), ctx.header("User-Agent"));
        ctx.redirect(landing(issued.token(), existing.isPresent() ? "/account/security?linked=1" : "/account"));
    }

    private Credentials readCredentials(Context ctx) {
        Credentials creds;
        try {
            creds = json.readValue(ctx.body(), Credentials.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return null;
        }
        if (creds.email() == null
                || creds.email().isBlank()
                || creds.password() == null
                || creds.password().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("email and password are required");
            return null;
        }
        return creds;
    }

    private void issueAndWrite(Context ctx, Account account, Long discordId, HttpStatus status) {
        JwtService.Issued issued = jwtService.issue(account.id(), discordId);
        accountSessions.record(issued.jti(), account.id(), issued.expiresAt(), ctx.header("User-Agent"));
        ctx.status(status)
                .json(new LoginResponse(
                        issued.token(),
                        issued.expiresAt().toString(),
                        toMePayload(account, discordId == null ? null : new MiniLink(discordId))));
    }

    private Object toMePayload(Account account, AccountIdentity link) {
        return toMePayload(account, link == null ? null : new MiniLink(link.externalIdAsLong()));
    }

    private Object toMePayload(Account account, MiniLink link) {
        return new AccountResponse(
                account.id(),
                account.email(),
                account.emailVerified(),
                account.hasPassword(),
                link == null ? null : Long.toString(link.discordUserId()),
                account.displayName(),
                account.theme(),
                account.darkMode());
    }

    public Optional<JwtService.Verified> currentSession(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return Optional.empty();
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) return Optional.empty();
        Optional<JwtService.Verified> verified = jwtService.verify(token);
        if (verified.isEmpty()) return Optional.empty();
        if (revokedJtis.isRevoked(verified.get().jti())) return Optional.empty();
        return verified;
    }

    /**
     * A page that sends the browser somewhere, for the paths that end without a session to store.
     */
    /**
     * Where the browser goes once Discord has vouched for somebody: the page that keeps the session.
     *
     * <p>The token travels in the fragment, which a browser never sends to a server, so it stays out of
     * every access log between here and there. A page written from here with an inline script would
     * do the same job, and is refused by the content security policy every response carries.
     */
    static String landing(String token, String next) {
        return "/auth/discord#token=%s&next=%s"
                .formatted(
                        URLEncoder.encode(token, StandardCharsets.UTF_8),
                        URLEncoder.encode(next, StandardCharsets.UTF_8));
    }

    private static String randomState() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    public record Credentials(String email, String password) {}

    public record LoginResponse(String token, String expiresAt, Object account) {}

    public record AccountResponse(
            int id,
            String email,
            boolean emailVerified,
            boolean hasPassword,
            String discordId,
            String username,
            String theme,
            String darkMode) {}

    private record MiniLink(long discordUserId) {}
}
