package de.chojo.lyna.data.access;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.lyna.data.dao.LicenseGuild;
import net.dv8tion.jda.api.entities.Guild;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class Guilds {
    Cache<Long, LicenseGuild> guilds = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();
    public LicenseGuild guild(Guild guild) {
        try {
            return guilds.get(guild.getIdLong(), () -> new LicenseGuild(guild));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
