/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.guild;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.data.dao.settings.Settings;
import de.chojo.lyna.data.roles.RoleSync;
import de.chojo.lyna.feature.download.repository.DownloadTypes;
import de.chojo.lyna.feature.license.repository.LicenseUser;
import de.chojo.lyna.feature.license.repository.Licenses;
import de.chojo.lyna.feature.product.repository.Products;
import de.chojo.nexus.NexusRest;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * One guild's half of the data: its products, licenses, settings and download types.
 *
 * <p>Holds the guild's id rather than the gateway's object for it. Everything below reads the id,
 * and the few operations that genuinely need Discord take the member or role they act on as an
 * argument - so this whole subtree answers on an instance running without the bot.
 */
public class LicenseGuild {
    private final long guildId;
    private final NexusRest nexus;

    /**
     * Providing access to registered products on this guild.
     */
    Products products;

    private final Conf configuration;

    /**
     * Providing access to licenses created on this guild.
     */
    Licenses licenses;

    Settings settings;
    DownloadTypes downloadTypes;

    /**
     * The recently accessed users
     */
    Cache<Long, LicenseUser> users =
            CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();

    private final Guilds guilds;

    public Guilds guilds() {
        return guilds;
    }

    public LicenseGuild(long guildId, NexusRest nexus, Conf configuration, Guilds guilds) {
        this.guildId = guildId;
        this.guilds = guilds;
        this.nexus = nexus;
        this.products = new Products(this, nexus);
        this.configuration = configuration;
        this.licenses = new Licenses(this);
        this.settings = new Settings(this);
        this.downloadTypes = new DownloadTypes(this);
    }

    public Products products() {
        return products;
    }

    public Licenses licenses() {
        return licenses;
    }

    public Settings settings() {
        return settings;
    }

    public long guildId() {
        return guildId;
    }

    /**
     * How this guild's Discord roles are kept in step. Read at call time, never stored: a guild can
     * be cached here before the gateway has connected.
     */
    public RoleSync roles() {
        return guilds.roles();
    }

    public DownloadTypes downloadTypes() {
        return downloadTypes;
    }

    public LicenseUser user(Member user) {
        try {
            return users.get(user.getIdLong(), () -> new LicenseUser(this, user));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public NexusRest nexus() {
        return nexus;
    }

    public Conf configuration() {
        return configuration;
    }
}
