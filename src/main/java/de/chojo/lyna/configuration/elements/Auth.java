package de.chojo.lyna.configuration.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.Prop;
import dev.chojo.ocular.override.OverwritePrefix;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("AUTH")
public class Auth {
    @Overwrite(env = @Env, prop = @Prop)
    private String jwtSecret = "";
    @Overwrite(env = @Env, prop = @Prop)
    private long jwtExpirySeconds = 86400L;

    public String jwtSecret() {
        return jwtSecret;
    }

    public long jwtExpirySeconds() {
        return jwtExpirySeconds;
    }
}
