/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.access;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.roles.RoleSync;
import de.chojo.lyna.feature.account.service.AccountLinkService;
import de.chojo.nexus.NexusRest;
import net.dv8tion.jda.api.entities.Guild;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class Guilds {
    private final NexusRest nexus;
    private final Cache<Long, LicenseGuild> guilds =
            CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();
    private final Conf configuration;

    /**
     * How Discord roles are kept in step, once there is a gateway to keep them with.
     *
     * <p>Mutable and read at call time rather than handed to each {@link LicenseGuild} as it is
     * built: the HTTP layer starts before the bot does, and a guild cached in that window would
     * otherwise skip its role cleanup for as long as the cache holds it.
     */
    private volatile RoleSync roles = RoleSync.NOOP;

    private final AccountLinkService accountLinks;

    public Guilds(NexusRest nexus, Conf configuration, AccountLinkService accountLinks) {
        this.nexus = nexus;
        this.configuration = configuration;
        this.accountLinks = accountLinks;
    }

    /**
     * How a Discord member is resolved to the account that holds their licences.
     */
    public AccountLinkService accountLinks() {
        return accountLinks;
    }

    public LicenseGuild guild(Guild guild) {
        return guild(guild.getIdLong());
    }

    public LicenseGuild guild(long guildId) {
        try {
            return guilds.get(guildId, () -> new LicenseGuild(guildId, nexus, configuration, this));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public RoleSync roles() {
        return roles;
    }

    public void roles(RoleSync roles) {
        this.roles = roles;
    }
}
