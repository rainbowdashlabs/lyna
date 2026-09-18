/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.configuration.elements;

import de.chojo.lyna.configuration.elements.discord.OAuth;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;
import dev.chojo.ocular.override.Prop;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("DISCORD")
public class Discord {
    @Overwrite(env = @Env, prop = @Prop)
    private OAuth oauth = new OAuth();

    public OAuth oauth() {
        return oauth;
    }
}
