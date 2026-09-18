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
 * How artifacts are handed out.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("DOWNLOADS")
public class Downloads {
    /**
     * Hands the jar over untouched instead of stamping who downloaded it.
     *
     * <p>For working on the download path locally, where the stamping is slow and the answer is not
     * what is being tested. An instance serving real downloads wants it off: the stamp is what says
     * which copy of a file went to whom.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private boolean skipJarSigning = false;

    public boolean skipJarSigning() {
        return skipJarSigning;
    }
}
