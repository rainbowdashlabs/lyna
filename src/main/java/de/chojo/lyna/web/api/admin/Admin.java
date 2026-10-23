/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.elements.discord.OAuth;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.butler.repository.ButlerApplicationRepository;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.guild.LicenseGuild;
import de.chojo.lyna.feature.icon.service.ProductIconService;
import de.chojo.lyna.feature.instance.entity.InstanceSettings;
import de.chojo.lyna.feature.instance.repository.InstanceOperatorRepository;
import de.chojo.lyna.feature.instance.repository.InstanceSettingsRepository;
import de.chojo.lyna.feature.instance.service.AppearancePolicy;
import de.chojo.lyna.feature.kiosk.entity.KioskProduct;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.entity.Sharee;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.feature.license.service.LicenseSharingService;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.entity.ProductLinks;
import de.chojo.lyna.feature.product.repository.ProductLinksRepository;
import de.chojo.lyna.feature.product.repository.ProductRepository;
import de.chojo.lyna.feature.product.service.ProductFamilyService;
import de.chojo.lyna.feature.purchase.repository.KoFiProductRepository;
import de.chojo.lyna.feature.releasepost.repository.ReleaseWebhookRepository;
import de.chojo.lyna.feature.releasepost.service.ChannelTarget;
import de.chojo.lyna.feature.releasepost.service.GithubWebhookService;
import de.chojo.lyna.gateway.Gateway;
import de.chojo.lyna.web.api.auth.Auth;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;
import static org.slf4j.LoggerFactory.getLogger;

public class Admin {
    /** Long enough for a page, short enough that nobody pastes a book into a text field. */
    private static final int DESCRIPTION_LIMIT = 8000;

    private final OAuth oauthConfig;
    private final ProductIconService productIcons;

    private final LicenseService licenseService;
    private final LicenseSharingService licenseSharing;
    private static final Logger log = getLogger(Admin.class);

    private final Auth auth;
    private final Conf configuration;
    private final AccountRepository accounts;
    private final Guilds guilds;
    private final InstanceSettingsRepository instanceSettings;
    private final AppearancePolicy appearancePolicy;
    private final KoFiProductRepository kofi;
    private final KioskProductRepository kioskProducts;
    private final ButlerApplicationRepository butlerApplications;
    private final ReleaseWebhookRepository releaseWebhooks;
    private final GithubWebhookService releasePosts;
    private final ProductRepository productRepository;
    private final ProductLinksRepository productLinks;
    private final ProductFamilyService productFamily;
    private final GuildAdminGuard guard;
    private final InstanceOperatorRepository operators;
    private final IconUrls iconUrls = new IconUrls();
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final Gateway gateway;

    @Inject
    public Admin(
            Auth auth,
            Conf configuration,
            AccountRepository accounts,
            Guilds guilds,
            InstanceSettingsRepository instanceSettings,
            AppearancePolicy appearancePolicy,
            KoFiProductRepository kofi,
            KioskProductRepository kioskProducts,
            InstanceOperatorRepository operators,
            Gateway gateway,
            LicenseService licenseService,
            LicenseSharingService licenseSharing,
            OAuth oauthConfig,
            ProductIconService productIcons,
            ButlerApplicationRepository butlerApplications,
            ReleaseWebhookRepository releaseWebhooks,
            GithubWebhookService releasePosts,
            ProductRepository productRepository,
            ProductLinksRepository productLinks,
            ProductFamilyService productFamily,
            GuildAdminGuard guard) {
        this.productRepository = productRepository;
        this.productLinks = productLinks;
        this.productFamily = productFamily;
        this.guard = guard;
        this.productIcons = productIcons;
        this.butlerApplications = butlerApplications;
        this.releaseWebhooks = releaseWebhooks;
        this.releasePosts = releasePosts;
        this.oauthConfig = oauthConfig;
        this.licenseService = licenseService;
        this.licenseSharing = licenseSharing;
        this.gateway = gateway;
        this.auth = auth;
        this.configuration = configuration;
        this.accounts = accounts;
        this.guilds = guilds;
        this.instanceSettings = instanceSettings;
        this.appearancePolicy = appearancePolicy;
        this.kofi = kofi;
        this.kioskProducts = kioskProducts;
        this.operators = operators;
    }

    public void init() {
        path("admin", () -> {
            get("guilds", this::listAdminGuilds);
            get("access", this::adminAccess);
            path("g/{guildId}", () -> {
                get("products", this::listProducts);
                post("products", this::createProduct);
                put("products/{productId}", this::updateProduct);
                delete("products/{productId}", this::deleteProduct);
                put("products/{productId}/description", this::setProductDescription);
                put("products/{productId}/page-source", this::setPageSource);
                post("products/{productId}/icon", this::uploadProductIcon);
                delete("products/{productId}/icon", this::deleteProductIcon);
                get("products/{productId}/release-webhook", this::releaseWebhook);
                post("products/{productId}/release-webhook", this::issueReleaseWebhook);
                put("products/{productId}/release-webhook/channel", this::releaseWebhookChannel);
                put("products/{productId}/release-webhook/activity", this::releaseWebhookActivity);
                delete("products/{productId}/release-webhook", this::removeReleaseWebhook);
                get("licenses", this::listLicenses);
                post("licenses", this::createLicense);
                get("registrations/{discordId}", this::registrationInfo);
                get("settings", this::getSettings);
                put("settings", this::updateSettings);
                get("kofi", this::listKofi);
                post("kofi", this::createKofi);
                get("trial", this::listTrialProducts);
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

    /**
     * What the caller may administer: the guilds, and whether the instance itself.
     *
     * <p>Operator status is said outright rather than left to be read off the guilds. An instance
     * running no bot, configuring no guild and holding no products has no guild to list, and its
     * operator would otherwise be told there was nothing to administer - including the instance area,
     * which is where that is put right.
     */
    private void adminAccess(Context ctx) {
        Optional<JwtService.Verified> session = auth.currentSession(ctx);
        if (session.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return;
        }
        Long discordId = resolveDiscordId(session.get());
        boolean operator = isOperator(discordId);
        ctx.json(new AdminAccess(operator, adminGuilds(discordId, operator)));
    }

    private void listProducts(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var kioskById = kioskProducts.all().stream().collect(Collectors.toMap(KioskProduct::id, product -> product));
        Map<Integer, Integer> butlerIds = butlerApplications.butlerIdsByProduct();
        List<Product> products = resolved.guild().products().all();
        ctx.json(products.stream().map(p -> summary(p, kioskById, butlerIds)).toList());
    }

    private ProductSummary summary(Product p, Map<Integer, KioskProduct> kioskById, Map<Integer, Integer> butlerIds) {
        ProductLinks links = productLinks.get(p.id());
        return new ProductSummary(
                p.id(),
                p.name(),
                p.url(),
                Long.toString(p.role()),
                p.free(),
                p.trial(),
                kioskById.containsKey(p.id()) ? kioskById.get(p.id()).iconUrl() : null,
                kioskById.containsKey(p.id()) ? kioskById.get(p.id()).description() : null,
                butlerIds.get(p.id()),
                kioskById.containsKey(p.id()) && kioskById.get(p.id()).pageReadme(),
                links.wikiUrl(),
                links.reviewUrl(),
                kioskById.containsKey(p.id()) ? kioskById.get(p.id()).parentId() : null);
    }

    /**
     * Points a product's tile at a hosted image, or takes the image away when the address is blank.
     *
     * <p>The address is checked before it is stored: an operator who mistypes it should be told so
     * here rather than by a storefront tile that has quietly lost its icon.
     */
    /**
     * Writes what a product says about itself on its own page.
     *
     * <p>Markdown, kept as it was written. It is rendered where it is shown rather than here, so
     * nothing stored has already decided what markup a reader gets.
     */
    private void setProductDescription(Context ctx) {
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
        ProductDescription body;
        try {
            body = json.readValue(ctx.body(), ProductDescription.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        String description = body == null || body.description() == null
                ? ""
                : body.description().strip();
        if (description.length() > DESCRIPTION_LIMIT) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .result("A description is at most %d characters".formatted(DESCRIPTION_LIMIT));
            return;
        }
        kioskProducts.description(productId, description.isBlank() ? null : description);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Changes everything about a product that is not its icon.
     *
     * <p>One endpoint rather than one per field: a form that saves a name and then fails on the role
     * has already changed half of what somebody asked for.
     */
    private void updateProduct(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var product = productFromPath(ctx, resolved);
        if (product == null) return;
        ProductEdit body;
        try {
            body = json.readValue(ctx.body(), ProductEdit.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        if (body == null || body.name() == null || body.name().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("A product needs a name");
            return;
        }
        if (body.description() != null && body.description().length() > DESCRIPTION_LIMIT) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .result("A description is at most %d characters".formatted(DESCRIPTION_LIMIT));
            return;
        }
        long roleId;
        try {
            roleId = body.roleId() == null || body.roleId().isBlank() ? 0L : Long.parseLong(body.roleId());
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("That is not a role id");
            return;
        }
        ProductLinks links = new ProductLinks(blankToNull(body.wikiUrl()), blankToNull(body.reviewUrl()));
        if ((links.wikiUrl() != null && !isWeb(links.wikiUrl()))
                || (links.reviewUrl() != null && !isWeb(links.reviewUrl()))) {
            ctx.status(HttpStatus.BAD_REQUEST).result("The wiki and review addresses have to be http or https");
            return;
        }
        if (body.butlerId() != null && body.butlerId() <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST).result("An UpdateButler id is a positive number");
            return;
        }
        if (body.butlerId() != null && butlerApplications.takenByAnother(body.butlerId(), product.id())) {
            ctx.status(HttpStatus.CONFLICT).result("Another product already carries that UpdateButler id");
            return;
        }
        String parentRefusal = parentRefusal(productFamily.check(product.id(), body.parentId()));
        if (parentRefusal != null) {
            ctx.status(HttpStatus.BAD_REQUEST).result(parentRefusal);
            return;
        }
        product.name(body.name().strip());
        product.url(
                body.url() == null || body.url().isBlank() ? null : body.url().strip());
        product.role(roleId);
        product.free(body.free());
        product.trial(body.trial());
        if (body.description() != null) {
            String description = body.description().strip();
            kioskProducts.description(product.id(), description.isBlank() ? null : description);
        }
        if (body.butlerId() == null) {
            butlerApplications.clear(product.id());
        } else {
            butlerApplications.assign(product.id(), body.butlerId());
        }
        productLinks.set(product.id(), links);
        productFamily.parent(product.id(), body.parentId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * @return what to tell an operator whose choice of parent was refused, or null when it was not
     */
    private static String parentRefusal(ProductFamilyService.ParentChange verdict) {
        return switch (verdict) {
            case OK, NO_SUCH_PRODUCT -> null;
            case SELF -> "A product cannot be an add-on of itself";
            case NO_SUCH_PARENT -> "That product does not exist in this guild";
            case PARENT_IS_ADDON -> "That product is an add-on itself, and an add-on has no add-ons";
            case HAS_ADDONS -> "This product has add-ons, so it cannot be an add-on itself";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static boolean isWeb(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /**
     * Sets whether the product's page shows its GitHub README instead of its description.
     */
    private void setPageSource(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var product = productFromPath(ctx, resolved);
        if (product == null) return;
        PageSource body;
        try {
            body = json.readValue(ctx.body(), PageSource.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        kioskProducts.pageReadme(product.id(), body != null && body.readme());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Deletes a product and everything attached to it, as {@code /products delete} does. The body must
     * name the product, so a stray request cannot delete one by its id alone.
     */
    private void deleteProduct(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var product = productFromPath(ctx, resolved);
        if (product == null) return;
        ProductDeletion body;
        try {
            body = json.readValue(ctx.body(), ProductDeletion.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        if (body == null || !product.name().equals(body.confirmName())) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Type the product's name to delete it");
            return;
        }
        productIcons.remove(product.id());
        product.delete();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void releaseWebhook(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var product = productFromPath(ctx, resolved);
        if (product == null) return;
        releaseWebhooks
                .ofProduct(product.id())
                .ifPresentOrElse(
                        webhook -> ctx.json(releaseWebhookView(webhook)), () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    /**
     * Gives a product a webhook address and secret, or new ones. The old address stops working, which
     * is what rotating is for.
     */
    private void issueReleaseWebhook(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var product = productFromPath(ctx, resolved);
        if (product == null) return;
        ctx.json(releaseWebhookView(releasePosts.issue(product.id())));
    }

    /**
     * Sets where releases are announced.
     */
    private void releaseWebhookChannel(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var product = productFromPath(ctx, resolved);
        if (product == null) return;
        ReleaseChannel body;
        try {
            body = json.readValue(ctx.body(), ReleaseChannel.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("That is not a channel id");
            return;
        }
        Long channelId = postableChannel(ctx, resolved, body.channelId());
        if (channelId == null) return;
        ctx.status(releaseWebhooks.channel(product.id(), channelId) ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }

    /**
     * Sets where pushes, pull requests and issues are posted, and which of them. Checked like the
     * release channel.
     */
    private void releaseWebhookActivity(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var product = productFromPath(ctx, resolved);
        if (product == null) return;
        ReleaseActivity body;
        try {
            body = json.readValue(ctx.body(), ReleaseActivity.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        Long channelId = postableChannel(ctx, resolved, body.channelId());
        if (channelId == null) return;
        var activity = new ReleaseWebhookRepository.Activity(
                body.push(), body.prOpened(), body.prMerged(), body.issueOpened(), body.issueClosed());
        ctx.status(
                releaseWebhooks.activity(product.id(), channelId, activity)
                        ? HttpStatus.NO_CONTENT
                        : HttpStatus.NOT_FOUND);
    }

    /**
     * Reads a channel id an operator typed. With a bot connected the channel must be one of this
     * guild's that messages can be sent to; without one there is nothing to ask.
     *
     * @return the id, 0 for none, or null when the request has been answered with what is wrong
     */
    private Long postableChannel(Context ctx, Resolved resolved, String raw) {
        long channelId;
        try {
            channelId = raw == null || raw.isBlank() ? 0 : Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("That is not a channel id");
            return null;
        }
        if (channelId != 0
                && gateway.connected()
                && discordGuild(resolved.guild().guildId())
                        .flatMap(guild -> ChannelTarget.resolve(List.of(guild), channelId))
                        .isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("This guild has no channel with that id the bot can post in");
            return null;
        }
        return channelId;
    }

    private void removeReleaseWebhook(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var product = productFromPath(ctx, resolved);
        if (product == null) return;
        releaseWebhooks.remove(product.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private ReleaseWebhookView releaseWebhookView(ReleaseWebhookRepository.ReleaseWebhook webhook) {
        return new ReleaseWebhookView(
                "%s/api/v1/webhook/github/%s"
                        .formatted(configuration.main().api().url(), webhook.token()),
                webhook.secret(),
                webhook.channelId() == 0 ? null : Long.toString(webhook.channelId()),
                webhook.activityChannelId() == 0 ? null : Long.toString(webhook.activityChannelId()),
                webhook.activity());
    }

    /**
     * Takes an uploaded image as a product's icon.
     *
     * <p>What the upload says it is does not decide anything: the service reads the type out of the
     * first bytes, and refuses what it cannot resize.
     */
    private void uploadProductIcon(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var product = productFromPath(ctx, resolved);
        if (product == null) return;
        var upload = ctx.uploadedFile("icon");
        if (upload == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result("No file was uploaded");
            return;
        }
        try {
            byte[] data = upload.content().readAllBytes();
            var stored = productIcons.store(product.id(), data);
            if (stored.isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST).result("That is not a PNG, JPEG or WebP image");
                return;
            }
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result(e.getMessage());
        } catch (Exception e) {
            log.warn("Could not store the icon for product {}", product.id(), e);
            ctx.status(HttpStatus.BAD_REQUEST).result("That image could not be read");
        }
    }

    private void deleteProductIcon(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var product = productFromPath(ctx, resolved);
        if (product == null) return;
        productIcons.remove(product.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * @return the product the path names, within the guild being administered, or null having already
     *         answered
     */
    private Product productFromPath(Context ctx, Resolved resolved) {
        int productId;
        try {
            productId = Integer.parseInt(ctx.pathParam("productId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
        var product = resolved.guild().products().byId(productId);
        if (product.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
        return product.get();
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
        Optional<Product> product;
        if (gateway.connected()) {
            var role = discordGuild(resolved.guild().guildId())
                    .map(g -> g.getRoleById(body.roleId()))
                    .orElse(null);
            if (role == null) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Unknown role");
                return;
            }
            product = resolved.guild().products().create(body.name(), role, body.url(), body.free(), body.trial());
        } else {
            product = resolved.guild()
                    .products()
                    .create(body.name(), body.roleId(), body.url(), body.free(), body.trial());
        }
        if (product.isEmpty()) {
            ctx.status(HttpStatus.CONFLICT).result("Product already exists");
            return;
        }
        var p = product.get();
        ctx.status(HttpStatus.CREATED)
                .json(new ProductSummary(
                        p.id(),
                        p.name(),
                        p.url(),
                        Long.toString(p.role()),
                        p.free(),
                        p.trial(),
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null));
    }

    private void listLicenses(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        List<LicenseSummary> out = new ArrayList<>();
        for (License l : resolved.guild().licenses().all()) {
            out.add(new LicenseSummary(
                    l.id(),
                    l.product().id(),
                    l.product().name(),
                    l.userIdentifier(),
                    licenseService.owner(l),
                    licenseSharing.shareCount(l)));
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
        if (body.productId() == null
                || body.identifier() == null
                || body.identifier().isBlank()) {
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
        licenseService.grantInitialAccess(l, body.releaseTypes());
        ctx.status(HttpStatus.CREATED)
                .json(new LicenseDetail(
                        l.id(),
                        l.product().id(),
                        l.product().name(),
                        l.userIdentifier(),
                        l.key(),
                        licenseService.owner(l),
                        licenseSharing.sharees(l).stream().map(Sharee::name).toList()));
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
        Member member = discordGuild(resolved.guild().guildId())
                .map(g -> g.getMemberById(discordId))
                .orElse(null);
        var owned = resolved.guild().licenses().byOwner(discordId).stream()
                .map(l -> new LicenseSummary(
                        l.id(),
                        l.product().id(),
                        l.product().name(),
                        l.userIdentifier(),
                        licenseService.owner(l),
                        licenseSharing.shareCount(l)))
                .toList();
        var shared = resolved.guild().licenses().bySharee(discordId).stream()
                .map(l -> new LicenseSummary(
                        l.id(),
                        l.product().id(),
                        l.product().name(),
                        l.userIdentifier(),
                        licenseService.owner(l),
                        licenseSharing.shareCount(l)))
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
        if (!kofi.create(product.get(), body.linkCode())) {
            ctx.status(HttpStatus.CONFLICT).result("This Ko-fi code belongs to another guild");
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listTrialProducts(Context ctx) {
        var resolved = requireGuildAdmin(ctx);
        if (resolved == null) return;
        var s = resolved.guild().settings().trial();
        var products = resolved.guild().products().all().stream()
                .filter(Product::trial)
                .map(p -> new ProductSummary(
                        p.id(),
                        p.name(),
                        p.url(),
                        Long.toString(p.role()),
                        p.free(),
                        p.trial(),
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null))
                .toList();
        ctx.json(new TrialInfo(
                (int) s.serverTime().toMinutes(), (int) s.accountTime().toMinutes(), products));
    }

    /**
     * @param adminRoleId the role whose members administer this guild here, or nothing for none
     */
    public record GuildSettings(int shares, int trialServerMinutes, int trialAccountMinutes, String adminRoleId) {}

    public record KofiMapping(String linkCode, Integer productId, String productName) {}

    public record TrialInfo(int serverMinutes, int accountMinutes, List<ProductSummary> products) {}

    private void instanceSystem(Context ctx) {
        if (!requireOperator(ctx)) return;
        int guildCount = gateway.guilds().size();
        String version;
        try (var in = getClass().getResourceAsStream("/version")) {
            version = in == null ? "unknown" : new String(in.readAllBytes()).trim();
        } catch (Exception e) {
            version = "unknown";
        }
        ctx.json(new SystemInfo(version, guildCount, gateway.connected(), oauthConfig.configured()));
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
        try {
            instanceSettings.update(appearancePolicy.validated(body));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result(e.getMessage());
            return;
        }
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

    /**
     * @param botConnected whether a gateway is connected at all. Worth stating plainly rather than
     *                     leaving an operator to infer it from a guild count of zero, now that
     *                     running without a bot is a supported way to run.
     */
    /**
     * @param discordOauth whether somebody can sign in with Discord, which needs a client id, a
     *                     secret and a redirect uri; without them the sign-in link goes nowhere useful
     */
    public record SystemInfo(String version, int guildCount, boolean botConnected, boolean discordOauth) {}

    /**
     * The guild the path names, and only for somebody entitled to administer it.
     *
     * <p>Two ways in. With a gateway, holding MANAGE_SERVER on the guild is the floor, and an
     * operator passes regardless. Without one there is no membership to check, so an operator is the
     * only way in - which is what lets an instance running no bot still be administered.
     *
     * <p>An operator is still identified by a Discord id, so an account that never linked Discord
     * cannot administer anything even bot-less. That is a limit of how operators are named, not of
     * this check.
     */
    private Resolved requireGuildAdmin(Context ctx) {
        var admin = guard.require(ctx);
        return admin == null ? null : new Resolved(admin.guild(), admin.callerDiscordId(), admin.operator());
    }

    /**
     * The gateway's object for a guild, when there is a gateway.
     *
     * <p>Only the handful of admin operations that act on Discord itself - granting a role, naming a
     * member - need this; the rest read the guild's own tables through its id.
     */
    private Optional<Guild> discordGuild(long guildId) {
        return gateway.guild(guildId);
    }

    private Long resolveDiscordId(JwtService.Verified verified) {
        return guard.discordId(verified);
    }

    private void listOperators(Context ctx) {
        if (!requireOperator(ctx)) return;
        List<OperatorView> configured = configuration.main().baseSettings().owners().stream()
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
        ctx.status(HttpStatus.CREATED)
                .json(new OperatorView(
                        Long.toString(discordId),
                        addedBy == null ? null : Long.toString(addedBy),
                        Instant.now(),
                        false));
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
        if (configuration.main().baseSettings().owners().isEmpty() && operators.count() <= 1) {
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
    private List<AdminGuild> knownGuilds() {
        Set<Long> ids = new TreeSet<>(productRepository.guildsWithProducts());
        long configured = configuration.main().baseSettings().botGuild();
        if (configured != 0) ids.add(configured);
        return ids.stream()
                .map(id -> new AdminGuild(Long.toString(id), "Guild " + id, null, "operator"))
                .toList();
    }

    private boolean isOperator(Long discordId) {
        return guard.isOperator(discordId);
    }

    /**
     * Whether an id holds the instance by configuration rather than by grant. Those cannot be
     * withdrawn through the web.
     */
    private boolean isRootOperator(long discordId) {
        return configuration.main().baseSettings().isOwner(discordId);
    }

    /**
     * Whether an id administers one guild.
     *
     * <p>Holding MANAGE_SERVER is the floor. A guild that would rather not hand out a server-wide
     * permission for this can name a role instead, and its members administer that guild here and
     * nothing else.
     */
    private boolean hasGuildAdmin(Long discordId, Guild guild) {
        return guard.hasGuildAdmin(discordId, guild);
    }

    /**
     * The guilds the caller may administer. Without a gateway there are no guild names or memberships
     * to ask about, so only an operator gets any: the configured guild and every guild that has
     * products, named by id.
     */
    private List<AdminGuild> adminGuilds(Long discordId, boolean operator) {
        if (!gateway.connected()) return operator ? knownGuilds() : List.of();
        Set<Long> seen = new HashSet<>();
        List<AdminGuild> result = new ArrayList<>();
        for (Guild g : gateway.guilds()) {
            if (!seen.add(g.getIdLong())) continue;
            if (operator) {
                result.add(new AdminGuild(Long.toString(g.getIdLong()), g.getName(), g.getIconUrl(), "operator"));
            } else if (hasGuildAdmin(discordId, g)) {
                result.add(new AdminGuild(Long.toString(g.getIdLong()), g.getName(), g.getIconUrl(), "guild_admin"));
            }
        }
        return result;
    }

    public record AdminGuild(String id, String name, String iconUrl, String role) {}

    /**
     * @param operator whether the caller administers the whole instance
     * @param guilds   the guilds the caller may administer
     */
    public record AdminAccess(boolean operator, List<AdminGuild> guilds) {}

    /**
     * @param roleId   as text, because a Discord id is larger than a JavaScript number holds exactly -
     *                 sent as one it comes back rounded, and the edit form would write the wrong role
     * @param parentId the product this one is an add-on of, or null
     */
    public record ProductSummary(
            int id,
            String name,
            String url,
            String roleId,
            boolean free,
            boolean trial,
            String iconUrl,
            String description,
            Integer butlerId,
            boolean pageReadme,
            String wikiUrl,
            String reviewUrl,
            Integer parentId) {}

    public record ProductDescription(String description) {}

    /**
     * @param confirmName the product's name, typed out by whoever is deleting it
     */
    public record ProductDeletion(String confirmName) {}

    /**
     * @param readme whether the product page shows the GitHub README instead of the description
     */
    public record PageSource(boolean readme) {}

    /**
     * @param url       what to paste into GitHub as the payload URL
     * @param secret    what to paste into GitHub as the secret
     * @param channelId as text, since a Discord id is larger than a JavaScript number holds exactly
     */
    /**
     * @param activityChannelId where pushes, pull requests and issues are posted, or nothing
     * @param activity          which of those are posted
     */
    public record ReleaseWebhookView(
            String url,
            String secret,
            String channelId,
            String activityChannelId,
            ReleaseWebhookRepository.Activity activity) {}

    public record ReleaseActivity(
            String channelId,
            boolean push,
            boolean prOpened,
            boolean prMerged,
            boolean issueOpened,
            boolean issueClosed) {}

    public record ReleaseChannel(String channelId) {}

    /**
     * @param roleId   the Discord role, as text because a role id does not fit a JavaScript number
     * @param parentId the product this one becomes an add-on of, or null to make it stand on its own
     */
    public record ProductEdit(
            String name,
            String url,
            String roleId,
            boolean free,
            boolean trial,
            String description,
            String iconUrl,
            Integer butlerId,
            String wikiUrl,
            String reviewUrl,
            Integer parentId) {}

    /**
     * @param configured whether the id holds the instance by configuration, and so cannot be
     *                   withdrawn here
     */
    public record OperatorView(String discordId, String addedBy, Instant addedAt, boolean configured) {}

    public record OperatorRequest(String discordId) {}

    public record CreateProduct(String name, String url, Long roleId, boolean free, boolean trial) {}

    /**
     * @param shareeCount everybody holding a place on the licence, including invites nobody has
     *                    answered and sharees who have no Discord id
     */
    public record LicenseSummary(
            int id, int productId, String productName, String identifier, long owner, int shareeCount) {}

    /**
     * @param sharees everybody holding a place on the licence, including invites nobody has answered
     *                and sharees who have no Discord id
     */
    public record LicenseDetail(
            int id,
            int productId,
            String productName,
            String identifier,
            String key,
            long owner,
            List<String> sharees) {}

    /**
     * @param releaseTypes what the licence reaches, or nothing for stable builds only
     */
    public record CreateLicense(Integer productId, String identifier, List<ReleaseType> releaseTypes) {}

    public record RegistrationInfo(
            long discordId,
            String memberName,
            List<LicenseSummary> ownedLicenses,
            List<LicenseSummary> sharedLicenses) {}

    private record Resolved(LicenseGuild guild, Long callerDiscordId, boolean operator) {}
}
