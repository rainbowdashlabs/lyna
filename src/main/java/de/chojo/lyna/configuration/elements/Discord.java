/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.configuration.elements;

import de.chojo.lyna.configuration.elements.discord.OAuth;
import dev.chojo.ocular.override.OverwritePrefix;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("DISCORD")
public class Discord {
    private OAuth oauth = new OAuth();

    public OAuth oauth() {
        return oauth;
    }
}
