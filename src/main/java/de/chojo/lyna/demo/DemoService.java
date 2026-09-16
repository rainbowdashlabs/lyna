package de.chojo.lyna.demo;

import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.data.access.DemoArtifacts;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.AccountIdentity;
import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Fills a demo instance with something to look at, and takes it away again.
 *
 * <p>The seed is written backwards from the screens rather than from the tables: every product,
 * licence and download below exists because some page is empty or untestable without it. A storefront
 * with one product does not show what the storefront does.
 *
 * <p>Everything is attached to the guild named in {@code baseSettings.botGuild}, and the accounts to
 * people who are really in it - an admin screen that resolves a member shows nothing for an id that
 * was invented. A guild too small to take a cast from is said so rather than half-seeded.
 */
public class DemoService {
    private static final Logger log = getLogger(DemoService.class);

    /** What every seeded account signs in with, when a password is used at all. */
    public static final String PASSWORD = "demo";

    private final Data data;
    private final Conf configuration;
    private final DemoArtifacts artifacts;
    private final PasswordHasher passwordHasher = new PasswordHasher();
    private ShardManager shardManager;

    public DemoService(Data data, Conf configuration) {
        this.data = data;
        this.configuration = configuration;
        this.artifacts = data.demoArtifacts();
    }

    public void shardManager(ShardManager shardManager) {
        this.shardManager = shardManager;
    }

    public boolean enabled() {
        return configuration.main().demo().enabled();
    }

    /**
     * Throws away what a previous seed made and lays it out again.
     *
     * @return what was seeded, or nothing when it could not be
     */
    public synchronized Optional<DemoData> reseed() {
        reset();
        return seed();
    }

    /**
     * Takes back everything a seed made, and nothing else.
     *
     * <p>Products and accounts carry the rest with them: a licence belongs to a product, a session
     * and a link belong to an account, and both cascade. What is left is the record itself.
     */
    public synchronized void reset() {
        for (String id : artifacts.of(DemoArtifacts.ACCOUNT)) {
            data.accounts().delete(Integer.parseInt(id));
        }
        Optional<LicenseGuild> guild = licenseGuild();
        if (guild.isPresent()) {
            for (String id : artifacts.of(DemoArtifacts.PRODUCT)) {
                guild.get().products().byId(Integer.parseInt(id)).ifPresent(Product::delete);
            }
            for (String id : artifacts.of(DemoArtifacts.DOWNLOAD_TYPE)) {
                guild.get().downloadTypes().byId(Integer.parseInt(id)).ifPresent(DownloadType::delete);
            }
        }
        artifacts.clear();
        log.info("[demo] the seeded data has been taken back");
    }

    /**
     * @return what a demo login may sign in as, or nothing when nothing has been seeded
     */
    public List<DemoAccount> accounts() {
        List<DemoAccount> out = new ArrayList<>();
        for (String id : artifacts.of(DemoArtifacts.ACCOUNT)) {
            data.accounts().findById(Integer.parseInt(id)).ifPresent(account -> out.add(new DemoAccount(
                    account.email(),
                    roleOf(out.size()),
                    describe(out.size()))));
        }
        return out;
    }

    private synchronized Optional<DemoData> seed() {
        Optional<LicenseGuild> licenseGuild = licenseGuild();
        if (licenseGuild.isEmpty()) {
            log.warn("[demo] no guild to seed: baseSettings.botGuild names one the bot is not in");
            return Optional.empty();
        }
        Guild guild = shardManager.getGuildById(licenseGuild.get().guildId());
        List<Member> members = guild.getMembers().stream().filter(member -> !member.getUser().isBot()).toList();
        if (members.size() < 2) {
            log.warn("[demo] the guild has {} member(s) to seed a cast from, and two are needed",
                    members.size());
            return Optional.empty();
        }

        var seeded = new DemoData(
                seedCatalogue(licenseGuild.get(), guild),
                seedAccounts(members));
        seedLicences(licenseGuild.get(), seeded, members);
        seedSettings(licenseGuild.get());
        log.info("[demo] seeded {} product(s) and {} account(s) in {}",
                seeded.products().size(), seeded.accounts().size(), guild.getName());
        return Optional.of(seeded);
    }

    /**
     * The catalogue: one of each answer the storefront can give, and a product with two release types
     * so the download wizard has a first step to show.
     */
    private List<Product> seedCatalogue(LicenseGuild licenseGuild, Guild guild) {
        DownloadType stable = create(licenseGuild, "Stable", "The build everybody runs", ReleaseType.STABLE);
        DownloadType dev = create(licenseGuild, "Dev", "The next one, early", ReleaseType.DEV);

        List<Product> products = new ArrayList<>();
        products.add(product(licenseGuild, guild, "Demo Free", "https://example.invalid/free", true, false,
                stable, dev));
        products.add(product(licenseGuild, guild, "Demo Premium", null, false, true, stable, dev));
        products.add(product(licenseGuild, guild, "Demo Unsellable", null, false, false, stable));
        return products;
    }

    private DownloadType create(LicenseGuild licenseGuild, String name, String description, ReleaseType type) {
        DownloadType created = licenseGuild.downloadTypes().create(name, description, type)
                .orElseThrow(() -> new IllegalStateException("Could not create the demo download type " + name));
        artifacts.record(DemoArtifacts.DOWNLOAD_TYPE, Integer.toString(created.id()));
        return created;
    }

    /**
     * One product, downloadable under each of the types it is given.
     *
     * <p>The role is the guild's own everybody-role. A demo is not demonstrating role management, and
     * making roles in somebody's guild to show a storefront would be a poor trade.
     */
    private Product product(LicenseGuild licenseGuild, Guild guild, String name, String url, boolean free,
                            boolean trial, DownloadType... types) {
        Product product = licenseGuild.products().create(name, guild.getPublicRole(), url, free, trial)
                .orElseThrow(() -> new IllegalStateException("Could not create the demo product " + name));
        artifacts.record(DemoArtifacts.PRODUCT, Integer.toString(product.id()));
        for (DownloadType type : types) {
            product.downloads().create(type, "releases", "de.chojo", "e2e-plugin", null);
        }
        product.mailings().create(name, """
                [{"type":"heading","text":"Thank you for your purchase"},
                 {"type":"paragraph","text":"Hi {{ name }}, here is your licence for {{ product }}."},
                 {"type":"key","label":"Your licence key"},
                 {"type":"button","label":"Download {{ product }}","url":"{{ downloadUrl }}"}]
                """);
        product.mailings().get().ifPresent(mailing -> mailing.blocks(mailing.mailText()));
        return product;
    }

    /**
     * The cast: an operator, somebody who owns a licence, somebody it was shared with, somebody with
     * nothing, and somebody who never linked Discord at all. The first four are linked to real
     * members, because the admin screens resolve them; the last deliberately is not, because a
     * sharee with no Discord is a case the pages have to handle and cannot otherwise be seen.
     */
    private List<Account> seedAccounts(List<Member> members) {
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < Math.min(ROLES.length, members.size()); i++) {
            Account account = data.accounts().create(
                    "demo-%s@example.invalid".formatted(ROLES[i]), passwordHasher.hash(PASSWORD));
            data.accounts().confirmEmail(account.id(), account.email());
            data.accounts().link(account.id(), members.get(i).getIdLong(), AccountIdentity.Verification.OAUTH,
                    members.get(i).getUser().getName());
            artifacts.record(DemoArtifacts.ACCOUNT, Integer.toString(account.id()));
            accounts.add(account);
        }
        data.instanceOperators().add(members.getFirst().getIdLong(), null);
        accounts.add(seedWebOnlyAccount());
        return accounts;
    }

    /**
     * Somebody who signed up and never linked Discord. Named by hand, which is the only way an
     * account gets a name when no provider is supplying one.
     */
    private Account seedWebOnlyAccount() {
        Account account = data.accounts().create("demo-web-only@example.invalid", passwordHasher.hash(PASSWORD));
        data.accounts().confirmEmail(account.id(), account.email());
        data.accounts().setUsername(account.id(), "webonly");
        artifacts.record(DemoArtifacts.ACCOUNT, Integer.toString(account.id()));
        return data.accounts().findById(account.id()).orElse(account);
    }

    /**
     * Licences, and the history of them being used, so the account area has something on every page.
     */
    private void seedLicences(LicenseGuild licenseGuild, DemoData seeded, List<Member> members) {
        if (seeded.accounts().size() < 2) return;
        long owner = members.get(1).getIdLong();
        long sharee = members.size() > 2 ? members.get(2).getIdLong() : members.get(0).getIdLong();

        for (Product product : seeded.products()) {
            if (product.free()) continue;
            Optional<License> licence = product.createLicense("demo-owner@example.invalid");
            if (licence.isEmpty()) continue;
            licence.get().grantAccess(ReleaseType.STABLE);
            data.accountLicenses().addSharee(licence.get().id(),
                    de.chojo.lyna.data.access.Accounts.accountIdForDiscord(sharee));
            seeded.accounts().stream()
                    .filter(account -> "demo-web-only@example.invalid".equals(account.email()))
                    .findFirst()
                    .ifPresent(webOnly -> data.accountLicenses().addSharee(licence.get().id(), webOnly.id()));
            data.licenseInvites().invite(licence.get().id(), "demo-invited@example.invalid");
            claim(licence.get(), owner);
            seedDownloads(product, seeded, licence.get());
        }
    }

    /**
     * Records the owner against the licence without going through Discord: the seed is describing a
     * state, not acting out somebody claiming one.
     */
    private void claim(License licence, long discordId) {
        de.chojo.sadu.queries.api.query.Query
                .query("INSERT INTO user_license(account_id, license_id) VALUES(?,?) ON CONFLICT DO NOTHING")
                .single(de.chojo.sadu.queries.api.call.Call.call()
                        .bind(de.chojo.lyna.data.access.Accounts.accountIdForDiscord(discordId))
                        .bind(licence.id()))
                .insert();
    }

    /** Downloads spread over months, so the history page's filters and paging have something to cut. */
    private void seedDownloads(Product product, DemoData seeded, License licence) {
        var downloads = product.downloads().downloads();
        if (downloads.isEmpty() || seeded.accounts().isEmpty()) return;
        int downloadId = downloads.getFirst().id();
        for (int i = 0; i < 8; i++) {
            Account account = seeded.accounts().get(i % seeded.accounts().size());
            data.downloadLog().record(account.id(), null, licence.id(), product.id(), downloadId,
                    "1.%d.0".formatted(i), i % 2 == 0 ? "license" : "sub_license", "Demo seed", null);
        }
    }

    private void seedSettings(LicenseGuild licenseGuild) {
        licenseGuild.settings().license().shares(4);
        licenseGuild.settings().trial().serverTime(Duration.ofMinutes(60));
        licenseGuild.settings().trial().accountTime(Duration.ofMinutes(120));
    }

    private Optional<LicenseGuild> licenseGuild() {
        long guildId = configuration.main().baseSettings().botGuild();
        if (shardManager == null || guildId == 0 || shardManager.getGuildById(guildId) == null) {
            return Optional.empty();
        }
        return Optional.of(data.guilds().guild(guildId));
    }

    private static final String[] ROLES = {"operator", "owner", "sharee", "newcomer"};

    private static String roleOf(int index) {
        return index < ROLES.length ? ROLES[index] : "web-only";
    }

    private static String describe(int index) {
        return switch (index) {
            case 0 -> "Administers the instance and every guild";
            case 1 -> "Owns licences and has shared one";
            case 2 -> "Had a licence shared with them";
            case 3 -> "Has an account and nothing else";
            default -> "Never linked Discord: holds a share through the web alone";
        };
    }

    /**
     * @param email what to sign in as
     * @param role  a short name for the part this account plays
     */
    public record DemoAccount(String email, String role, String description) {
    }

    public record DemoData(List<Product> products, List<Account> accounts) {
    }

    /**
     * @return when the data was last laid out, for saying so on the page
     */
    public Instant seededAt() {
        return Instant.now();
    }
}
