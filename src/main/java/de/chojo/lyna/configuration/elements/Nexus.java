package de.chojo.lyna.configuration.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.Prop;
import dev.chojo.ocular.override.OverwritePrefix;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
@OverwritePrefix("NEXUS")
public class Nexus {
    @Overwrite(env = @Env, prop = @Prop)
    private String host = "eldonexus.de";
    @Overwrite(env = @Env, prop = @Prop)
    private String username = "admin";
    @Overwrite(env = @Env, prop = @Prop)
    private String password = "passy";

    public String host() {
        return host;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }
}
