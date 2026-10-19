/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.mail.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.configuration.elements.Links;
import de.chojo.lyna.feature.guild.repository.GuildSettingsRepository;
import de.chojo.lyna.feature.icon.repository.ProductIconRepository;
import de.chojo.lyna.feature.kiosk.entity.KioskProduct;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.mail.entity.MailGuildSettings;
import de.chojo.lyna.feature.mail.entity.Mailing;
import de.chojo.lyna.feature.mail.repository.MailGuildSettingsRepository;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.entity.ProductLinks;
import de.chojo.lyna.feature.product.repository.ProductLinksRepository;
import de.chojo.lyna.mail.blocks.DefaultPurchaseMail;
import de.chojo.lyna.mail.blocks.MailBlockRenderer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Decides what a purchase mail says: which blocks it is composed from, and what its placeholders
 * stand for.
 *
 * <p>A product whose mail has no body of its own sends its guild's default, and a guild that never
 * wrote one sends the default the application ships. That is what lets a product be sold without
 * anybody writing a mail for it.
 */
@Singleton
public class PurchaseMailService {
    private final MailGuildSettingsRepository guildMail;
    private final ProductLinksRepository productLinks;
    private final ProductIconRepository icons;
    private final KioskProductRepository kioskProducts;
    private final GuildSettingsRepository guildSettings;
    private final Links links;

    @Inject
    public PurchaseMailService(
            MailGuildSettingsRepository guildMail,
            ProductLinksRepository productLinks,
            ProductIconRepository icons,
            KioskProductRepository kioskProducts,
            GuildSettingsRepository guildSettings,
            Links links) {
        this.guildMail = guildMail;
        this.productLinks = productLinks;
        this.icons = icons;
        this.kioskProducts = kioskProducts;
        this.guildSettings = guildSettings;
        this.links = links;
    }

    /**
     * @return the default mail the application ships, as a block document
     */
    public String builtInDefault() {
        return DefaultPurchaseMail.BLOCKS;
    }

    /**
     * @return the guild's default mail, or the shipped one when the guild never wrote its own
     */
    public String guildDefault(long guildId) {
        String stored = guildMail.get(guildId).blocks();
        return Mailing.hasBlocks(stored) ? stored : DefaultPurchaseMail.BLOCKS;
    }

    /**
     * The blocks a product's mail is rendered from, or nothing when its mail is HTML written before
     * blocks existed and is sent as it is.
     */
    public String blocksFor(Mailing mailing) {
        if (Mailing.hasBlocks(mailing.blocks())) return mailing.blocks();
        if (mailing.hasOwnBody()) return null;
        return guildDefault(mailing.product().guildId());
    }

    /**
     * Everything a purchase mail's placeholders may name, for one product and one buyer.
     *
     * @param name who bought it, as they gave it
     * @param key  the licence key
     */
    public Map<String, Object> values(Product product, String name, String key) {
        MailGuildSettings guild = guildMail.get(product.guildId());
        ProductLinks own = productLinks.get(product.id());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("key", key);
        values.put("product", product.name());
        values.put("downloadUrl", product.url());
        values.put("productUrl", product.url());
        values.put("productPage", frontend() + "/products/" + product.id());
        values.put("productIcon", icon(product.id()));
        values.put("wikiUrl", own.wikiUrl());
        values.put("reviewUrl", own.reviewUrl());
        values.put("discordUrl", guild.discordUrl());
        values.put("shares", guildSettings.shares(product.guildId()));
        values.put(MailBlockRenderer.LINKS, guild.links());
        return values;
    }

    /**
     * The values a preview or a test mail shows: the product's own, with a stand-in buyer and key.
     */
    public Map<String, Object> sampleValues(Product product) {
        Map<String, Object> sample = MailBlockRenderer.sampleValues(product.name());
        return values(product, sample.get("name").toString(), sample.get("key").toString());
    }

    /**
     * Where a mail client fetches the product's icon: the one uploaded here, else the address an
     * operator configured, else nothing.
     */
    private String icon(int productId) {
        if (icons.of(productId).isPresent()) {
            return frontend() + "/api/v1/products/%d/icon".formatted(productId);
        }
        return kioskProducts.byId(productId).map(KioskProduct::iconUrl).orElse(null);
    }

    private String frontend() {
        String base = Objects.requireNonNullElse(links.frontend(), "");
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
