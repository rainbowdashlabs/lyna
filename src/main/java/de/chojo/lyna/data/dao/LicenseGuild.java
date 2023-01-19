package de.chojo.lyna.data.dao;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.TimeUnit;

public class LicenseGuild {
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
}
