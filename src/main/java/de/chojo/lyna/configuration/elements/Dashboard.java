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

/**
 * The hosted dashboard gateway, which asks Lyna whether a server's dashboard key is good.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("DASHBOARD")
public class Dashboard {
    /**
     * The secret the gateway sends as a bearer token when it verifies a key. Supply it as
     * {@code DASHBOARD_GATEWAYSECRET} rather than writing it here. Left blank, nothing can verify a
     * key, which is what an instance that hosts no gateway wants.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private String gatewaySecret = "";

    public String gatewaySecret() {
        return gatewaySecret;
    }
}
