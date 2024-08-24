package de.chojo.lyna.data.dao;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.jdautil.configuration.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.data.dao.downloadtype.DownloadTypes;
import de.chojo.lyna.data.dao.licenses.Licenses;
import de.chojo.lyna.data.dao.products.Products;
import de.chojo.lyna.data.dao.settings.Settings;
import de.chojo.nexus.NexusRest;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class LicenseGuild {
    private final Guild guild;
    private final NexusRest nexus;

    /**
     * Providing access to registered products on this guild.
     */
    Products products;
    private final Configuration<ConfigFile> configuration;

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

    public LicenseGuild(Guild guild, NexusRest nexus, Configuration<ConfigFile> configuration) {
        this.guild = guild;
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

    public NexusRest nexus() {
        return nexus;
    }

    public Configuration<ConfigFile> configuration() {
        return configuration;
    }
}
