package de.chojo.lyna.configuration.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.Prop;
import dev.chojo.ocular.override.OverwritePrefix;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal", "MismatchedQueryAndUpdateOfCollection"})
@OverwritePrefix("BOT")
public class BaseSettings {
    @Overwrite(env = @Env, prop = @Prop)
    private String token = "";
    @Overwrite(env = @Env, prop = @Prop)
    private boolean botEnabled = true;
    @Overwrite(env = @Env, prop = @Prop)
    private List<Long> botOwner = new ArrayList<>();
    @Overwrite(env = @Env, prop = @Prop)
    private long botGuild = 0L;

    public String token() {
        return token;
    }

    /**
     * Whether the Discord bot is started at all.
     *
     * <p>Off, the HTTP API comes up on its own. That is what lets the end-to-end stack run without a
     * gateway token, and what keeps a deployment that only serves downloads from needing one.
     *
     * @return whether to connect to the gateway
     */
    public boolean botEnabled() {
        return botEnabled;
    }

    public boolean isOwner(long id) {
        return botOwner.contains(id);
    }

    /**
     * The ids that administer the instance by configuration.
     *
     * <p>The root set: they are listed here rather than granted through the web, so nothing done
     * there can take the instance away from them.
     *
     * @return the configured operator ids
     */
    public List<Long> owners() {
        return List.copyOf(botOwner);
    }

    public long botGuild() {
        return botGuild;
    }
}
