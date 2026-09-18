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
