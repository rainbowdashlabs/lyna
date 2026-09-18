/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.configuration.elements;

import de.chojo.lyna.util.LicenseCreator;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;
import dev.chojo.ocular.override.Prop;

@OverwritePrefix("LICENSE")
public class License {
    @Overwrite(env = @Env, prop = @Prop)
    private String baseSeed = LicenseCreator.generateRandomSequence(50);

    public long baseSeed() {
        return baseSeed.hashCode();
    }
}
