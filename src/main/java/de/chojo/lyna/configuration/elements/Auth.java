/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.configuration.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;
import dev.chojo.ocular.override.Prop;

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
