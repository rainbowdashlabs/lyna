package de.chojo.lyna.data.dao;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.lyna.data.dao.platforms.Platforms;
import de.chojo.lyna.data.dao.products.Products;
import net.dv8tion.jda.api.entities.Guild;

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
     * The recently accessed users
     */
    Cache<Long, LicenseUser> users = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();

    public LicenseGuild(Guild guild) {
        this.guild = guild;
        this.platforms = new Platforms(this);
        this.products = new Products(this);
    }

    public Platforms platforms() {
        return platforms;
    }

    public Products products() {
        return products;
    }

    public long guildId() {
        return guild.getIdLong();
    }
}
