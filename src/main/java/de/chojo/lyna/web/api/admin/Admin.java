package de.chojo.lyna.web.api.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.jdautil.configuration.Configuration;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.dao.account.AccountIdentity;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.access.InstanceOperators;
import de.chojo.lyna.data.access.InstanceSettingsAccess;
import de.chojo.lyna.data.access.KioskProducts;
import de.chojo.lyna.data.access.KoFiProducts;
import de.chojo.lyna.data.dao.InstanceSettings;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.web.api.auth.Auth;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import java.util.Optional;
import java.util.Set;

import java.time.Duration;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;
import static org.slf4j.LoggerFactory.getLogger;

public class Admin {
    private static final Logger log = getLogger(Admin.class);

    private final Auth auth;
    private final Configuration<ConfigFile> configuration;
    private final Accounts accounts;
    private final Guilds guilds;
    private final InstanceSettingsAccess instanceSettings;
    private final KoFiProducts kofi;
    private final KioskProducts kioskProducts;
    private final InstanceOperators operators;
    private final IconUrls iconUrls = new IconUrls();
    private final de.chojo.lyna.mail.blocks.MailBlockRenderer blockRenderer =
            new de.chojo.lyna.mail.blocks.MailBlockRenderer();
    private final de.chojo.lyna.mail.MailingService mailingService;
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private ShardManager shardManager;

    public Admin(Auth auth, Configuration<ConfigFile> configuration, Accounts accounts, Guilds guilds,
                 InstanceSettingsAccess instanceSettings, KoFiProducts kofi,
                 KioskProducts kioskProducts, InstanceOperators operators,
                 de.chojo.lyna.mail.MailingService mailingService) {
        this.auth = auth;
        this.configuration = configuration;
        this.accounts = accounts;
        this.guilds = guilds;
        this.instanceSettings = instanceSettings;
        this.kofi = kofi;
        this.kioskProducts = kioskProducts;
        this.operators = operators;
        this.mailingService = mailingService;
    }

    public void shardManager(ShardManager shardManager) {
        this.shardManager = shardManager;
    }

    public void init() {
        path("admin", () -> {
            get("guilds", this::listAdminGuilds);
            path("g/{guildId}", () -> {
                get("products", this::listProducts);
                post("products", this::createProduct);
                put("products/{productId}/icon", this::setProductIcon);
                get("licenses", this::listLicenses);
                post("licenses", this::createLicense);
                get("registrations/{discordId}", this::registrationInfo);
                get("settings", this::getSettings);
                put("settings", this::updateSettings);
                get("kofi", this::listKofi);
                post("kofi", this::createKofi);
                get("trial", this::listTrialProducts);
                get("mailing", this::listMailing);
                put("mailing/{mailingId}", this::updateMailing);
                post("mailing/{mailingId}/preview", this::previewMailing);
                post("mailing/{mailingId}/test", this::testMailing);
            });
            path("instance", () -> {
                get("system", this::instanceSystem);
                get("appearance", this::instanceAppearance);
                put("appearance", this::instanceUpdateAppearance);
                get("operators", this::listOperators);
                post("operators", this::addOperator);
                delete("operators/{discordId}", this::removeOperator);
            });
        });
    }

    private void listAdminGuilds(Context ctx) {
        Optional<JwtService.Verified> session = auth.currentSession(ctx);
        if (session.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        Long discordId = resolveDiscordId(session.get());
        boolean operator = isOperator(discordId);
        ctx.json(adminGuilds(discordId, operator));
    }

    private void listProducts(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var icons = kioskProducts.all().stream()
                .collect(java.util.stream.Collectors.toMap(
                        de.chojo.lyna.data.dao.products.KioskProduct::id,
                        product -> java.util.Optional.ofNullable(product.iconUrl())));
        List<Product> products = resolved.guild().products().all();
        ctx.json(products.stream()
                .map(p -> new ProductSummary(p.id(), p.name(), p.url(), p.role(), p.free(),
                        icons.getOrDefault(p.id(), java.util.Optional.empty()).orElse(null)))
                .toList());
    }

    /**
     * The product whose mail the path names, within the guild being administered.
     *
     * <p>Looked up through the guild rather than by id alone, so a mailing belonging to another
     * guild cannot be reached by guessing its number.
     */
    private de.chojo.lyna.data.dao.products.mailings.Mailing requireMailing(Context ctx, Resolved resolved) {
        int mailingId;
        try {
            mailingId = Integer.parseInt(ctx.pathParam("mailingId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
        for (Product product : resolved.guild().products().all()) {
            var mailing = product.mailings().get();
            if (mailing.isPresent() && mailing.get().id() == mailingId) return mailing.get();
        }
        ctx.status(HttpStatus.NOT_FOUND);
        return null;
    }

    private void updateMailing(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var mailing = requireMailing(ctx, resolved);
        if (mailing == null) return;
        MailingBlocks body;
        try {
            body = json.readValue(ctx.body(), MailingBlocks.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        String blocks = body == null ? null : body.blocks();
        try {
            blockRenderer.render(blocks, de.chojo.lyna.mail.blocks.MailBlockRenderer
                    .sampleValues(mailing.product().name()));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result(e.getMessage());
            return;
        }
        mailing.blocks(blocks);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Renders what the editor is holding, so the page can show the mail rather than an impression of
     * it. The blocks come from the request rather than from the database, so a preview shows what
     * has not been saved yet.
     */
    private void previewMailing(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var mailing = requireMailing(ctx, resolved);
        if (mailing == null) return;
        MailingBlocks body;
        try {
            body = json.readValue(ctx.body(), MailingBlocks.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        var values = de.chojo.lyna.mail.blocks.MailBlockRenderer.sampleValues(mailing.product().name());
        String rendered;
        try {
            rendered = body == null || body.blocks() == null
                    ? blockRenderer.render(mailing.blocks(), values)
                    : blockRenderer.render(body.blocks(), values);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result(e.getMessage());
            return;
        }
        var context = new java.util.HashMap<String, Object>(values);
        context.put("body", rendered);
        ctx.contentType("text/html").result(mailingService.renderer().render("licence-custom", "en", context));
    }

    /**
     * Sends the mail to one address, which is the only way to see what a mail client makes of it.
     */
    private void testMailing(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var mailing = requireMailing(ctx, resolved);
        if (mailing == null) return;
        MailingTest body;
        try {
            body = json.readValue(ctx.body(), MailingTest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        String address = body == null || body.address() == null ? "" : body.address().trim();
        if (!address.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            ctx.status(HttpStatus.BAD_REQUEST).result("That is not an email address");
            return;
        }
        var sample = de.chojo.lyna.mail.blocks.MailBlockRenderer.sampleValues(mailing.product().name());
        var mail = de.chojo.lyna.mail.MailCreator.createLicenseMessage(mailingService.renderer(), mailing,
                sample.get("key").toString(), sample.get("name").toString(), address, null);
        mailingService.sendMail(mail);
        ctx.status(HttpStatus.ACCEPTED);
    }

    /**
     * Points a product's tile at a hosted image, or takes the image away when the address is blank.
     *
     * <p>The address is checked before it is stored: an operator who mistypes it should be told so
     * here rather than by a storefront tile that has quietly lost its icon.
     */
    private void setProductIcon(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        int productId;
        try {
            productId = Integer.parseInt(ctx.pathParam("productId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        if (resolved.guild().products().byId(productId).isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        ProductIcon body;
        try {
            body = json.readValue(ctx.body(), ProductIcon.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        String url = body == null || body.iconUrl() == null ? "" : body.iconUrl().trim();
        if (!url.isBlank()) {
            var rejection = iconUrls.reject(url);
            if (rejection.isPresent()) {
                ctx.status(HttpStatus.BAD_REQUEST).result(rejection.get());
                return;
            }
        }
        kioskProducts.iconUrl(productId, url);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void createProduct(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        CreateProduct body;
        try {
            body = json.readValue(ctx.body(), CreateProduct.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        if (body.name() == null || body.name().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("name required");
            return;
        }
        if (body.roleId() == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result("roleId required");
            return;
        }
        var role = discordGuild(resolved.guild().guildId()).map(g -> g.getRoleById(body.roleId())).orElse(null);
        if (role == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Unknown role");
            return;
        }
        var product = resolved.guild().products().create(body.name(), role, body.url(), body.free(), body.trial());
        if (product.isEmpty()) {
            ctx.status(HttpStatus.CONFLICT).result("Product already exists");
            return;
        }
        var p = product.get();
        ctx.status(HttpStatus.CREATED).json(new ProductSummary(p.id(), p.name(), p.url(), p.role(), p.free(), null));
    }

    private void listLicenses(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        List<LicenseSummary> out = new ArrayList<>();
        for (License l : resolved.guild().licenses().all()) {
            out.add(new LicenseSummary(l.id(), l.product().id(), l.product().name(),
                    l.userIdentifier(), l.owner(), l.shareCount()));
        }
        ctx.json(out);
    }

    private void createLicense(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        CreateLicense body;
        try {
            body = json.readValue(ctx.body(), CreateLicense.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        if (body.productId() == null || body.identifier() == null || body.identifier().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("productId and identifier required");
            return;
        }
        var product = resolved.guild().products().byId(body.productId());
        if (product.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result("Unknown product");
            return;
        }
        Optional<License> license = resolved.guild().licenses().create(product.get(), body.identifier());
        if (license.isEmpty()) {
            ctx.status(HttpStatus.CONFLICT).result("License with this identifier already exists for this product");
            return;
        }
        var l = license.get();
        ctx.status(HttpStatus.CREATED).json(new LicenseDetail(l.id(), l.product().id(), l.product().name(),
                l.userIdentifier(), l.key(), l.owner(),
                l.sharees().stream().map(License.Sharee::name).toList()));
    }

    private void registrationInfo(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        long discordId;
        try {
            discordId = Long.parseLong(ctx.pathParam("discordId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid discord id");
            return;
        }
        Member member = discordGuild(resolved.guild().guildId()).map(g -> g.getMemberById(discordId)).orElse(null);
        var owned = resolved.guild().licenses().byOwner(discordId).stream()
                .map(l -> new LicenseSummary(l.id(), l.product().id(), l.product().name(),
                        l.userIdentifier(), l.owner(), l.shareCount()))
                .toList();
        var shared = resolved.guild().licenses().bySharee(discordId).stream()
                .map(l -> new LicenseSummary(l.id(), l.product().id(), l.product().name(),
                        l.userIdentifier(), l.owner(), l.shareCount()))
                .toList();
        ctx.json(new RegistrationInfo(discordId, member != null ? member.getEffectiveName() : null, owned, shared));
    }

    private void getSettings(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var s = resolved.guild().settings();
        Long adminRole = s.license().adminRoleId();
        ctx.json(new GuildSettings(
                s.license().shares(),
                (int) s.trial().serverTime().toMinutes(),
                (int) s.trial().accountTime().toMinutes(),
                adminRole == null ? null : Long.toString(adminRole)));
    }

    private void updateSettings(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        GuildSettings body;
        try {
            body = json.readValue(ctx.body(), GuildSettings.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        if (body.shares() < 0 || body.trialServerMinutes() < 0 || body.trialAccountMinutes() < 0) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Negative values not allowed");
            return;
        }
        Long adminRole = null;
        if (body.adminRoleId() != null && !body.adminRoleId().isBlank()) {
            adminRole = parseDiscordId(body.adminRoleId());
            if (adminRole == null) {
                ctx.status(HttpStatus.BAD_REQUEST).result("That is not a role id");
                return;
            }
        }
        var s = resolved.guild().settings();
        s.license().shares(body.shares());
        s.license().adminRoleId(adminRole);
        s.trial().serverTime(Duration.ofMinutes(body.trialServerMinutes()));
        s.trial().accountTime(Duration.ofMinutes(body.trialAccountMinutes()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listKofi(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        ctx.json(kofi.listForGuild(resolved.guild().guildId()));
    }

    private void createKofi(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        KofiMapping body;
        try {
            body = json.readValue(ctx.body(), KofiMapping.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        if (body.linkCode() == null || body.linkCode().isBlank() || body.productId() == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result("linkCode and productId required");
            return;
        }
        var product = resolved.guild().products().byId(body.productId());
        if (product.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result("Unknown product");
            return;
        }
        kofi.create(product.get(), body.linkCode());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listTrialProducts(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var s = resolved.guild().settings().trial();
        var products = resolved.guild().products().all().stream()
                .map(p -> new ProductSummary(p.id(), p.name(), p.url(), p.role(), p.free(), null))
                .toList();
        ctx.json(new TrialInfo((int) s.serverTime().toMinutes(), (int) s.accountTime().toMinutes(), products));
    }

    private void listMailing(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var out = new ArrayList<MailingTemplate>();
        for (Product p : resolved.guild().products().all()) {
            p.mailings().get().ifPresent(m -> out.add(
                    new MailingTemplate(m.id(), p.id(), p.name(), m.name(), m.blocks(), m.mailText())));
        }
        ctx.json(out);
    }

    /**
     * @param adminRoleId the role whose members administer this guild here, or nothing for none
     */
    public record GuildSettings(int shares, int trialServerMinutes, int trialAccountMinutes, String adminRoleId) {
    }

    public record KofiMapping(String linkCode, Integer productId, String productName) {
    }

    public record TrialInfo(int serverMinutes, int accountMinutes, List<ProductSummary> products) {
    }

    /**
     * @param blocks   the mail as its operator composed it, or nothing for one written before
     * @param mailText the HTML a mail written before blocks still carries
     */
    public record MailingTemplate(int id, int productId, String productName, String name, String blocks,
                                  String mailText) {
    }

    private void instanceSystem(Context ctx) {
        if (!requireOperator(ctx)) return;
        int guildCount = shardManager == null ? 0 : shardManager.getGuilds().size();
        String version;
        try (var in = getClass().getResourceAsStream("/version")) {
            version = in == null ? "unknown" : new String(in.readAllBytes()).trim();
        } catch (Exception e) {
            version = "unknown";
        }
        ctx.json(new SystemInfo(version, guildCount));
    }

    private void instanceAppearance(Context ctx) {
        if (!requireOperator(ctx)) return;
        ctx.json(instanceSettings.get());
    }

    private void instanceUpdateAppearance(Context ctx) {
        if (!requireOperator(ctx)) return;
        InstanceSettings body;
        try {
            body = json.readValue(ctx.body(), InstanceSettings.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        instanceSettings.update(body);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private boolean requireOperator(Context ctx) {
        Optional<JwtService.Verified> session = auth.currentSession(ctx);
        if (session.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Long discordId = resolveDiscordId(session.get());
        if (!isOperator(discordId)) {
            ctx.status(HttpStatus.NOT_FOUND);
            return false;
        }
        return true;
    }

    public record SystemInfo(String version, int guildCount) {
    }

    private Resolved requireGuildAdmin(Context ctx) {
        Optional<JwtService.Verified> session = auth.currentSession(ctx);
        if (session.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return null;
        }
        long guildId;
        try {
            guildId = Long.parseLong(ctx.pathParam("guildId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid guild id");
            return null;
        }
        Guild guild = shardManager == null ? null : shardManager.getGuildById(guildId);
        if (guild == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
        Long discordId = resolveDiscordId(session.get());
        boolean operator = isOperator(discordId);
        if (!operator && !hasGuildAdmin(discordId, guild)) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
        return new Resolved(guilds.guild(guild), discordId, operator);
    }

    /**
     * The gateway's object for a guild, when there is a gateway.
     *
     * <p>Only the handful of admin operations that act on Discord itself - granting a role, naming a
     * member - need this; the rest read the guild's own tables through its id.
     */
    private Optional<Guild> discordGuild(long guildId) {
        return Optional.ofNullable(shardManager).map(manager -> manager.getGuildById(guildId));
    }

    private Long resolveDiscordId(JwtService.Verified verified) {
        if (verified.discordId() != null) return verified.discordId();
        return accounts.findLinkByAccountId(verified.accountId())
                .map(AccountIdentity::externalIdAsLong)
                .orElse(null);
    }

    private void listOperators(Context ctx) {
        if (!requireOperator(ctx)) return;
        List<OperatorView> configured = configuration.config().baseSettings().owners().stream()
                .map(id -> new OperatorView(Long.toString(id), null, null, true))
                .toList();
        List<OperatorView> granted = operators.all().stream()
                .filter(operator -> !isRootOperator(operator.discordId()))
                .map(operator -> new OperatorView(
                        Long.toString(operator.discordId()),
                        operator.addedBy() == null ? null : Long.toString(operator.addedBy()),
                        operator.addedAt(),
                        false))
                .toList();
        ctx.json(Stream.concat(configured.stream(), granted.stream()).toList());
    }

    private void addOperator(Context ctx) {
        if (!requireOperator(ctx)) return;
        OperatorRequest body;
        try {
            body = json.readValue(ctx.body(), OperatorRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        Long discordId = parseDiscordId(body == null ? null : body.discordId());
        if (discordId == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result("That is not a Discord id");
            return;
        }
        if (isRootOperator(discordId)) {
            ctx.status(HttpStatus.CONFLICT).result("That id already administers the instance by configuration");
            return;
        }
        Long addedBy = resolveDiscordId(auth.currentSession(ctx).orElseThrow());
        operators.add(discordId, addedBy);
        ctx.status(HttpStatus.CREATED).json(new OperatorView(Long.toString(discordId),
                addedBy == null ? null : Long.toString(addedBy), Instant.now(), false));
    }

    /**
     * Withdraws the instance from an id.
     *
     * <p>Two things it refuses. A configured id was never granted here and cannot be taken away
     * here - editing the configuration is the way to do that. And the last operator standing is
     * kept when the configuration names nobody, because removing them would leave the instance area
     * reachable by no one at all.
     */
    private void removeOperator(Context ctx) {
        if (!requireOperator(ctx)) return;
        Long discordId = parseDiscordId(ctx.pathParam("discordId"));
        if (discordId == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        if (isRootOperator(discordId)) {
            ctx.status(HttpStatus.CONFLICT)
                    .result("That id administers the instance by configuration. Edit the configuration to change it.");
            return;
        }
        if (configuration.config().baseSettings().owners().isEmpty() && operators.count() <= 1) {
            ctx.status(HttpStatus.CONFLICT)
                    .result("That is the last operator, and the configuration names none. Add another first.");
            return;
        }
        if (!operators.remove(discordId)) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * @return the text as a Discord id, or null when it is not one
     */
    private static Long parseDiscordId(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (!trimmed.matches("\\d{5,20}")) return null;
        return Long.parseLong(trimmed);
    }

    /**
     * Whether an id administers the whole instance.
     *
     * <p>Two sources, and the order matters only for what may be taken away: the configured ids are
     * the root set and cannot be removed through the web, so there is always a way back in; the rest
     * were granted here and can be withdrawn here.
     */
    private boolean isOperator(Long discordId) {
        if (discordId == null) return false;
        return isRootOperator(discordId) || operators.contains(discordId);
    }

    /**
     * Whether an id holds the instance by configuration rather than by grant. Those cannot be
     * withdrawn through the web.
     */
    private boolean isRootOperator(long discordId) {
        return configuration.config().baseSettings().isOwner(discordId);
    }

    /**
     * Whether an id administers one guild.
     *
     * <p>Holding MANAGE_SERVER is the floor. A guild that would rather not hand out a server-wide
     * permission for this can name a role instead, and its members administer that guild here and
     * nothing else.
     */
    private boolean hasGuildAdmin(Long discordId, Guild guild) {
        if (discordId == null) return false;
        Member member = guild.getMemberById(discordId);
        if (member == null) return false;
        if (member.hasPermission(Permission.MANAGE_SERVER)) return true;
        Long adminRole = guilds.guild(guild).settings().license().adminRoleId();
        if (adminRole == null) return false;
        return member.getRoles().stream().anyMatch(role -> role.getIdLong() == adminRole);
    }

    private List<AdminGuild> adminGuilds(Long discordId, boolean operator) {
        if (shardManager == null) return List.of();
        Set<Long> seen = new HashSet<>();
        List<AdminGuild> result = new ArrayList<>();
        for (Guild g : shardManager.getGuilds()) {
            if (!seen.add(g.getIdLong())) continue;
            if (operator) {
                result.add(new AdminGuild(Long.toString(g.getIdLong()), g.getName(), g.getIconUrl(), "operator"));
            } else if (hasGuildAdmin(discordId, g)) {
                result.add(new AdminGuild(Long.toString(g.getIdLong()), g.getName(), g.getIconUrl(), "guild_admin"));
            }
        }
        return result;
    }

    public record AdminGuild(String id, String name, String iconUrl, String role) {
    }

    public record ProductSummary(int id, String name, String url, long roleId, boolean free, String iconUrl) {
    }

    /**
     * @param configured whether the id holds the instance by configuration, and so cannot be
     *                   withdrawn here
     */
    public record OperatorView(String discordId, String addedBy, Instant addedAt, boolean configured) {
    }

    public record OperatorRequest(String discordId) {
    }

    public record MailingBlocks(String blocks) {
    }

    public record MailingTest(String address) {
    }

    public record ProductIcon(String iconUrl) {
    }

    public record CreateProduct(String name, String url, Long roleId, boolean free, boolean trial) {
    }

    /**
     * @param shareeCount everybody holding a place on the licence, including invites nobody has
     *                    answered and sharees who have no Discord id
     */
    public record LicenseSummary(int id, int productId, String productName, String identifier, long owner,
                                 int shareeCount) {
    }

    /**
     * @param shareeCount everybody holding a place on the licence, including invites nobody has
     *                    answered and sharees who have no Discord id
     */
    public record LicenseDetail(int id, int productId, String productName, String identifier, String key,
                                long owner, List<String> sharees) {
    }

    public record CreateLicense(Integer productId, String identifier) {
    }

    public record RegistrationInfo(long discordId, String memberName, List<LicenseSummary> ownedLicenses,
                                   List<LicenseSummary> sharedLicenses) {
    }

    private record Resolved(LicenseGuild guild, Long callerDiscordId, boolean operator) {
    }
}
