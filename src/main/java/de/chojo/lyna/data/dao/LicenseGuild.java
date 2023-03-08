package de.chojo.lyna.data.dao;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.lyna.data.dao.downloadtype.DownloadTypes;
import de.chojo.lyna.data.dao.licenses.Licenses;
import de.chojo.lyna.data.dao.platforms.Platforms;
import de.chojo.lyna.data.dao.products.Products;
import de.chojo.lyna.data.dao.products.downloads.Downloads;
import de.chojo.lyna.data.dao.settings.Settings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class LicenseGuild {
    private final Guild guild;
    /**
     * Providing access to registered platforms on this guild.
     */
    Platforms platforms;

    /**
     * Providing access to registered products on this guild.
     */
    Products products;

    /**
     * Providing access to licenses created on this guild.
     */
    Licenses licenses;

    Settings settings;
    DownloadTypes downloadTypes;

    /**
     * The recently accessed users
     */
    Cache<Long, LicenseUser> users = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();

    public LicenseGuild(Guild guild) {
        this.guild = guild;
        this.platforms = new Platforms(this);
        this.products = new Products(this);
        this.licenses = new Licenses(this);
        this.settings = new Settings(this);
        this.downloadTypes = new DownloadTypes(this);
    }

    public Platforms platforms() {
        return platforms;
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
        return guild.getIdLong();
    }

    public Guild guild() {
        return guild;
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
}
