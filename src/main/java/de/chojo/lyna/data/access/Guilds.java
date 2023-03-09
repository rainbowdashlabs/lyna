package de.chojo.lyna.data.access;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.nexus.NexusRest;
import net.dv8tion.jda.api.entities.Guild;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class Guilds {
    private final NexusRest nexus;
    private final Cache<Long, LicenseGuild> guilds = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();

    public Guilds(NexusRest nexus) {
        this.nexus = nexus;
    }

    public LicenseGuild guild(Guild guild) {
        try {
            return guilds.get(guild.getIdLong(), () -> new LicenseGuild(guild, nexus));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
