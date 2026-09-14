package de.chojo.lyna.web.api.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.jdautil.configuration.Configuration;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.access.Guilds;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import java.time.Duration;

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
    private final IconUrls iconUrls = new IconUrls();
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private ShardManager shardManager;

    public Admin(Auth auth, Configuration<ConfigFile> configuration, Accounts accounts, Guilds guilds,
                 InstanceSettingsAccess instanceSettings, KoFiProducts kofi,
                 KioskProducts kioskProducts) {
        this.auth = auth;
        this.configuration = configuration;
        this.accounts = accounts;
        this.guilds = guilds;
        this.instanceSettings = instanceSettings;
        this.kofi = kofi;
        this.kioskProducts = kioskProducts;
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
            });
            path("instance", () -> {
                get("system", this::instanceSystem);
                get("appearance", this::instanceAppearance);
                put("appearance", this::instanceUpdateAppearance);
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
        var role = resolved.guild().guild().getRoleById(body.roleId());
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
                    l.userIdentifier(), l.owner(), l.subUsers().size()));
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
                l.userIdentifier(), l.key(), l.owner(), l.subUsers()));
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
        Member member = resolved.guild().guild().getMemberById(discordId);
        var owned = resolved.guild().licenses().byOwner(discordId).stream()
                .map(l -> new LicenseSummary(l.id(), l.product().id(), l.product().name(),
                        l.userIdentifier(), l.owner(), l.subUsers().size()))
                .toList();
        var shared = resolved.guild().licenses().bySharee(discordId).stream()
                .map(l -> new LicenseSummary(l.id(), l.product().id(), l.product().name(),
                        l.userIdentifier(), l.owner(), l.subUsers().size()))
                .toList();
        ctx.json(new RegistrationInfo(discordId, member != null ? member.getEffectiveName() : null, owned, shared));
    }

    private void getSettings(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var s = resolved.guild().settings();
        ctx.json(new GuildSettings(
                s.license().shares(),
                (int) s.trial().serverTime().toMinutes(),
                (int) s.trial().accountTime().toMinutes()));
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
        var s = resolved.guild().settings();
        s.license().shares(body.shares());
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
            p.mailings().get().ifPresent(m -> out.add(new MailingTemplate(m.id(), p.id(), p.name(), m.name())));
        }
        ctx.json(out);
    }

    public record GuildSettings(int shares, int trialServerMinutes, int trialAccountMinutes) {
    }

    public record KofiMapping(String linkCode, Integer productId, String productName) {
    }

    public record TrialInfo(int serverMinutes, int accountMinutes, List<ProductSummary> products) {
    }

    public record MailingTemplate(int id, int productId, String productName, String name) {
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

    private Long resolveDiscordId(JwtService.Verified verified) {
        if (verified.discordId() != null) return verified.discordId();
        return accounts.findLinkByAccountId(verified.accountId())
                .map(link -> link.discordUserId())
                .orElse(null);
    }

    private boolean isOperator(Long discordId) {
        if (discordId == null) return false;
        return configuration.config().baseSettings().isOwner(discordId);
    }

    private boolean hasGuildAdmin(Long discordId, Guild guild) {
        if (discordId == null) return false;
        Member member = guild.getMemberById(discordId);
        if (member == null) return false;
        if (member.hasPermission(Permission.MANAGE_SERVER)) return true;
        // Optional admin-role override would live on license_settings.admin_role_id;
        // not wired yet — the MANAGE_SERVER check is the floor.
        return false;
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

    public record ProductIcon(String iconUrl) {
    }

    public record CreateProduct(String name, String url, Long roleId, boolean free, boolean trial) {
    }

    public record LicenseSummary(int id, int productId, String productName, String identifier, long owner,
                                 int shareeCount) {
    }

    public record LicenseDetail(int id, int productId, String productName, String identifier, String key,
                                long owner, List<Long> sharees) {
    }

    public record CreateLicense(Integer productId, String identifier) {
    }

    public record RegistrationInfo(long discordId, String memberName, List<LicenseSummary> ownedLicenses,
                                   List<LicenseSummary> sharedLicenses) {
    }

    private record Resolved(LicenseGuild guild, Long callerDiscordId, boolean operator) {
    }
}
