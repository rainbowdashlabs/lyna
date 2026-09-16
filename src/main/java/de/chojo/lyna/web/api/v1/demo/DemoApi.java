package de.chojo.lyna.web.api.v1.demo;

import com.google.inject.Inject;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.lyna.data.access.AccountSessions;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.demo.DemoService;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.data.dao.account.AccountIdentity;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;

import java.util.List;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * The three things a demo instance offers that a real one must not.
 *
 * <p>Signing in here needs no password, which is the whole point and also the whole risk. Every one
 * of these refuses outright unless {@code demo.enabled}, and the refusal is here rather than in the
 * page that hides the button.
 *
 * <p>They answer {@code 404} rather than {@code 403} when the mode is off, so an instance that is
 * not a demo does not advertise that it could be one.
 */
public class DemoApi {
    private static final Logger log = getLogger(DemoApi.class);

    private final DemoService demo;
    private final Accounts accounts;
    private final AccountSessions sessions;
    private final JwtService jwtService;
    private final ObjectMapper json = new ObjectMapper();

    @Inject
    public DemoApi(DemoService demo, Accounts accounts, AccountSessions sessions, JwtService jwtService) {
        this.demo = demo;
        this.accounts = accounts;
        this.sessions = sessions;
        this.jwtService = jwtService;
    }

    public void init() {
        path("demo", () -> {
            get("accounts", this::listAccounts);
            post("login", this::login);
            post("reset", this::reset);
        });
    }

    /**
     * @return false when the mode is off, having already answered
     */
    private boolean requireDemo(Context ctx) {
        if (demo.enabled()) return true;
        ctx.status(HttpStatus.NOT_FOUND);
        return false;
    }

    private void listAccounts(Context ctx) {
        if (!requireDemo(ctx)) return;
        List<DemoService.DemoAccount> cast = demo.accounts();
        ctx.json(cast);
    }

    /**
     * Signs in as one of the seeded accounts, without a password.
     *
     * <p>Only an account the seed made: the list of what may be signed in as is the list of what was
     * seeded, so a demo instance cannot be used to reach an account somebody made themselves.
     */
    private void login(Context ctx) {
        if (!requireDemo(ctx)) return;
        DemoLogin body;
        try {
            body = json.readValue(ctx.body(), DemoLogin.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Malformed request");
            return;
        }
        String email = body == null || body.email() == null ? "" : body.email().trim();
        boolean seeded = demo.accounts().stream().anyMatch(account -> account.email().equalsIgnoreCase(email));
        if (!seeded) {
            ctx.status(HttpStatus.UNAUTHORIZED).result("That is not one of the demo accounts");
            return;
        }
        var account = accounts.findByEmail(email);
        if (account.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED).result("That is not one of the demo accounts");
            return;
        }
        Long discordId = accounts.findLinkByAccountId(account.get().id())
                .map(AccountIdentity::externalIdAsLong)
                .orElse(null);
        var issued = jwtService.issue(account.get().id(), discordId);
        sessions.record(issued.jti(), account.get().id(), issued.expiresAt(), ctx.header("User-Agent"));
        accounts.touchLastLogin(account.get().id());
        log.info("[demo] signed in as {} without a password", email);
        ctx.json(new DemoSession(issued.token(), issued.expiresAt().toString()));
    }

    private void reset(Context ctx) {
        if (!requireDemo(ctx)) return;
        var seeded = demo.reseed();
        if (seeded.isEmpty()) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .result("Nothing could be seeded. The configured guild has to exist and have members.");
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record DemoLogin(String email) {
    }

    public record DemoSession(String token, String expiresAt) {
    }
}
