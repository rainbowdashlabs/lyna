/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.feature.mail.entity.MailGuildSettings;
import de.chojo.lyna.feature.mail.entity.MailLink;
import de.chojo.lyna.feature.mail.entity.Mailing;
import de.chojo.lyna.feature.mail.repository.MailGuildSettingsRepository;
import de.chojo.lyna.feature.mail.repository.MailingRepository;
import de.chojo.lyna.feature.mail.service.PurchaseMailService;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.purchase.service.PurchaseService;
import de.chojo.lyna.mail.Mail;
import de.chojo.lyna.mail.MailCreator;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.mail.PurchaseRecipient;
import de.chojo.lyna.mail.blocks.MailBlockRenderer;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

/**
 * A guild's purchase mails: the default every product without a mail of its own sends, each
 * product's own mail, and sending one by hand as {@code /mailing send} does.
 */
public class AdminMailing {
    private static final int MAX_LINKS = 10;
    private static final String PREVIEW_ADDRESS = "buyer@example.invalid";

    private final GuildAdminGuard guard;
    private final LicenseService licenses;
    private final PurchaseService purchases;
    private final PurchaseMailService purchaseMail;
    private final MailGuildSettingsRepository guildMail;
    private final MailingRepository mailings;
    private final MailingService mailingService;
    private final MailBlockRenderer blockRenderer = new MailBlockRenderer();
    private final ObjectMapper json = new ObjectMapper();

    public record MailingCreate(String name) {}

    public record MailingCreated(int id, String name) {}

    /**
     * @param name         how the mail greets the recipient
     * @param releaseTypes what the licence reaches, or nothing for stable builds only
     */
    public record MailingSend(String address, String name, List<ReleaseType> releaseTypes) {}

    /**
     * @param blocks      the mail as its operator composed it, or nothing
     * @param mailText    the HTML a mail written before blocks still carries
     * @param usesDefault whether the product sends the guild's default, having no body of its own
     * @param values      what each placeholder stands for in this product's mail, for the editor's hint
     */
    public record MailingTemplate(
            int id,
            int productId,
            String productName,
            String name,
            String blocks,
            String mailText,
            boolean usesDefault,
            Map<String, String> values) {}

    public record MailingBlocks(String blocks) {}

    public record MailingTest(String address) {}

    /**
     * @param blocks              the default as it is sent, the guild's own or the shipped one
     * @param custom              whether the guild wrote its own
     * @param builtIn             the shipped default, for loading it back into the editor
     * @param productsWithoutMail the products that cannot be sold through a receipt yet, having no mail
     */
    public record GuildMail(
            String blocks,
            boolean custom,
            String builtIn,
            String discordUrl,
            List<MailLink> links,
            List<String> productsWithoutMail) {}

    public record GuildMailContact(String discordUrl, List<MailLink> links) {}

    /**
     * @param blocks    what the editor holds
     * @param productId whose values to fill in, or nothing for the guild's first product
     */
    public record GuildMailPreview(String blocks, Integer productId) {}

    public record GuildMailTest(String address, Integer productId) {}

    public record MailingsCreated(int created) {}

    @Inject
    public AdminMailing(
            GuildAdminGuard guard,
            LicenseService licenses,
            PurchaseService purchases,
            PurchaseMailService purchaseMail,
            MailGuildSettingsRepository guildMail,
            MailingRepository mailings,
            MailingService mailingService) {
        this.guard = guard;
        this.licenses = licenses;
        this.purchases = purchases;
        this.purchaseMail = purchaseMail;
        this.guildMail = guildMail;
        this.mailings = mailings;
        this.mailingService = mailingService;
    }

    /**
     * Mounts the routes under the guild being administered.
     */
    public void init() {
        post("products/{productId}/mailing", this::create);
        post("products/{productId}/mailing/send", this::send);
        get("mailing", this::list);
        post("mailing", this::createMissing);
        put("mailing/{mailingId}", this::update);
        post("mailing/{mailingId}/preview", this::preview);
        post("mailing/{mailingId}/test", this::test);
        post("mailing/{mailingId}/use-default", this::useDefault);
        get("guild-mail", this::guildMail);
        put("guild-mail/blocks", this::saveGuildBlocks);
        delete("guild-mail/blocks", this::resetGuildBlocks);
        put("guild-mail/contact", this::saveContact);
        post("guild-mail/preview", this::previewGuildMail);
        post("guild-mail/test", this::testGuildMail);
    }

    private void create(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Product product = guard.product(ctx, admin);
        if (product == null) return;
        MailingCreate body = read(ctx, MailingCreate.class);
        if (body == null) return;
        if (body.name() == null || body.name().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("The mail needs a name for the product");
            return;
        }
        if (product.mailings().get().isPresent()) {
            ctx.status(HttpStatus.CONFLICT).result("This product has a mailing already");
            return;
        }
        Mailing mailing = product.mailings().create(body.name().strip(), "");
        ctx.status(HttpStatus.CREATED).json(new MailingCreated(mailing.id(), mailing.name()));
    }

    /**
     * Issues a license for the address and mails it there, reaching the release types asked for or
     * stable builds when none are named. An address
     * that has a license for the product already is sent that one again, never a second.
     */
    private void send(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Product product = guard.product(ctx, admin);
        if (product == null) return;
        MailingSend body = read(ctx, MailingSend.class);
        if (body == null) return;
        if (body.address() == null
                || !body.address().contains("@")
                || body.name() == null
                || body.name().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Name the recipient and their email address");
            return;
        }
        Optional<Mailing> mailing = product.mailings().get();
        if (mailing.isEmpty()) {
            ctx.status(HttpStatus.CONFLICT).result("This product has no mailing to send");
            return;
        }
        String address = body.address().strip();
        Optional<License> license = product.createLicense(address);
        if (license.isEmpty()) {
            ctx.status(HttpStatus.CONFLICT).result("A license already exists for this address");
            return;
        }
        licenses.grantInitialAccess(license.get(), body.releaseTypes());
        purchases.announce(mailing.get(), license.get(), body.name().strip(), address);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void list(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        var out = new ArrayList<MailingTemplate>();
        for (Product product : admin.guild().products().all()) {
            product.mailings()
                    .get()
                    .ifPresent(mailing -> out.add(new MailingTemplate(
                            mailing.id(),
                            product.id(),
                            product.name(),
                            mailing.name(),
                            mailing.blocks(),
                            mailing.mailText(),
                            !mailing.hasOwnBody(),
                            printable(purchaseMail.sampleValues(product)))));
        }
        ctx.json(out);
    }

    /**
     * Gives every product without a mail an empty one named after it, so each sends the guild's
     * default and a receipt naming it finds it.
     */
    private void createMissing(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        ctx.json(new MailingsCreated(mailings.createMissing(admin.guild().guildId())));
    }

    /**
     * Saves what the editor holds. An empty editor is the product handing its mail back to the
     * guild's default.
     */
    private void update(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Mailing mailing = requireMailing(ctx, admin);
        if (mailing == null) return;
        MailingBlocks body = read(ctx, MailingBlocks.class);
        if (body == null) return;
        if (!Mailing.hasBlocks(body.blocks())) {
            mailing.useDefault();
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }
        if (!renders(ctx, body.blocks())) return;
        mailing.blocks(body.blocks());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Renders what the editor is holding, so the page can show the mail rather than an impression of
     * it. The blocks come from the request rather than from the database, so a preview shows what
     * has not been saved yet; an empty editor previews the guild's default, which is what it would
     * send.
     */
    private void preview(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Mailing mailing = requireMailing(ctx, admin);
        if (mailing == null) return;
        MailingBlocks body = read(ctx, MailingBlocks.class);
        if (body == null) return;
        String blocks = Mailing.hasBlocks(body.blocks())
                ? body.blocks()
                : purchaseMail.guildDefault(admin.guild().guildId());
        renderPreview(ctx, blocks, mailing.product());
    }

    /**
     * Sends the mail as it is saved to one address, which is the only way to see what a mail client
     * makes of it.
     */
    private void test(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Mailing mailing = requireMailing(ctx, admin);
        if (mailing == null) return;
        MailingTest body = read(ctx, MailingTest.class);
        if (body == null) return;
        String address = address(ctx, body.address());
        if (address == null) return;
        sendSample(mailing.product(), purchaseMail.blocksFor(mailing), mailing.mailText(), address);
        ctx.status(HttpStatus.ACCEPTED);
    }

    private void useDefault(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Mailing mailing = requireMailing(ctx, admin);
        if (mailing == null) return;
        mailing.useDefault();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void guildMail(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        long guildId = admin.guild().guildId();
        MailGuildSettings settings = guildMail.get(guildId);
        List<String> withoutMail = admin.guild().products().all().stream()
                .filter(product -> product.mailings().get().isEmpty())
                .map(Product::name)
                .toList();
        ctx.json(new GuildMail(
                purchaseMail.guildDefault(guildId),
                Mailing.hasBlocks(settings.blocks()),
                purchaseMail.builtInDefault(),
                settings.discordUrl(),
                settings.links(),
                withoutMail));
    }

    private void saveGuildBlocks(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        MailingBlocks body = read(ctx, MailingBlocks.class);
        if (body == null) return;
        if (!Mailing.hasBlocks(body.blocks())) {
            ctx.status(HttpStatus.BAD_REQUEST).result("The default mail needs at least one block");
            return;
        }
        if (!renders(ctx, body.blocks())) return;
        guildMail.blocks(admin.guild().guildId(), body.blocks());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Goes back to the default the application ships, and to whatever improvements it gets later.
     */
    private void resetGuildBlocks(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        guildMail.blocks(admin.guild().guildId(), null);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Saves the Discord invite and the links every purchase mail of the guild fills in. Each address
     * is checked here, so a typo is reported to the operator rather than mailed to a buyer.
     */
    private void saveContact(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        GuildMailContact body = read(ctx, GuildMailContact.class);
        if (body == null) return;
        String discordUrl = body.discordUrl() == null || body.discordUrl().isBlank()
                ? null
                : body.discordUrl().strip();
        if (discordUrl != null && !isWeb(discordUrl)) {
            ctx.status(HttpStatus.BAD_REQUEST).result("The Discord invite has to be an http or https address");
            return;
        }
        List<MailLink> links = new ArrayList<>();
        for (MailLink link : body.links() == null ? List.<MailLink>of() : body.links()) {
            if (link == null || link.label() == null || link.label().isBlank() || link.url() == null) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Every link needs a label and an address");
                return;
            }
            if (!isWeb(link.url().strip())) {
                ctx.status(HttpStatus.BAD_REQUEST)
                        .result("The address of %s has to be http or https"
                                .formatted(link.label().strip()));
                return;
            }
            links.add(new MailLink(link.label().strip(), link.url().strip()));
        }
        if (links.size() > MAX_LINKS) {
            ctx.status(HttpStatus.BAD_REQUEST).result("At most %d links".formatted(MAX_LINKS));
            return;
        }
        guildMail.contact(admin.guild().guildId(), discordUrl, links);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void previewGuildMail(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        GuildMailPreview body = read(ctx, GuildMailPreview.class);
        if (body == null) return;
        Product product = sampleProduct(ctx, admin, body.productId());
        if (product == null) return;
        String blocks = Mailing.hasBlocks(body.blocks())
                ? body.blocks()
                : purchaseMail.guildDefault(admin.guild().guildId());
        renderPreview(ctx, blocks, product);
    }

    private void testGuildMail(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        GuildMailTest body = read(ctx, GuildMailTest.class);
        if (body == null) return;
        String address = address(ctx, body.address());
        if (address == null) return;
        Product product = sampleProduct(ctx, admin, body.productId());
        if (product == null) return;
        sendSample(product, purchaseMail.guildDefault(admin.guild().guildId()), null, address);
        ctx.status(HttpStatus.ACCEPTED);
    }

    /**
     * The product whose values a guild-wide preview fills in: the one asked for, or the guild's
     * first. A guild without products has nothing to preview a purchase of.
     */
    private Product sampleProduct(Context ctx, GuildAdminGuard.GuildAdmin admin, Integer productId) {
        List<Product> products = admin.guild().products().all();
        Optional<Product> product = productId == null
                ? products.stream().findFirst()
                : products.stream().filter(p -> p.id() == productId).findFirst();
        if (product.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result("This guild has no product to preview the mail for");
            return null;
        }
        return product.get();
    }

    /**
     * The product whose mail the path names, within the guild being administered.
     *
     * <p>Looked up through the guild rather than by id alone, so a mailing belonging to another
     * guild cannot be reached by guessing its number.
     */
    private Mailing requireMailing(Context ctx, GuildAdminGuard.GuildAdmin admin) {
        int mailingId;
        try {
            mailingId = Integer.parseInt(ctx.pathParam("mailingId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
        for (Product product : admin.guild().products().all()) {
            var mailing = product.mailings().get();
            if (mailing.isPresent() && mailing.get().id() == mailingId) return mailing.get();
        }
        ctx.status(HttpStatus.NOT_FOUND);
        return null;
    }

    private void renderPreview(Context ctx, String blocks, Product product) {
        if (!renders(ctx, blocks)) return;
        Mail mail = MailCreator.createLicenseMessage(
                mailingService.renderer(),
                blocks,
                null,
                purchaseMail.sampleValues(product),
                PREVIEW_ADDRESS,
                PurchaseRecipient.UNSTATED);
        ctx.contentType("text/html").result(mail.text());
    }

    private void sendSample(Product product, String blocks, String html, String address) {
        mailingService.sendMail(MailCreator.createLicenseMessage(
                mailingService.renderer(),
                blocks,
                html,
                purchaseMail.sampleValues(product),
                address,
                PurchaseRecipient.UNSTATED));
    }

    /**
     * Whether the blocks are a block document, answering the request with what is wrong when not.
     */
    private boolean renders(Context ctx, String blocks) {
        try {
            blockRenderer.render(blocks, MailBlockRenderer.sampleValues("Preview"));
            return true;
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result(e.getMessage());
            return false;
        }
    }

    private static String address(Context ctx, String raw) {
        String address = raw == null ? "" : raw.trim();
        if (!address.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            ctx.status(HttpStatus.BAD_REQUEST).result("That is not an email address");
            return null;
        }
        return address;
    }

    private <T> T read(Context ctx, Class<T> type) {
        try {
            T body = json.readValue(ctx.body(), type);
            if (body == null) ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return body;
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return null;
        }
    }

    private static boolean isWeb(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /**
     * The values a placeholder can be written as, as the editor shows them: an unset one as empty.
     */
    private static Map<String, String> printable(Map<String, Object> values) {
        Map<String, String> out = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (!(value instanceof Collection<?>)) out.put(key, value == null ? "" : value.toString());
        });
        return out;
    }
}
