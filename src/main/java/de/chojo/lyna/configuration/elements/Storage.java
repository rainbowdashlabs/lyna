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
 * Where lyna keeps the files it is given.
 *
 * <p>A directory beside the application rather than a bucket somewhere: the only thing stored is a
 * handful of product icons, and an instance that runs a database and a mailbox should not also have
 * to run object storage for that.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("STORAGE")
public class Storage {
    @Overwrite(env = @Env, prop = @Prop)
    private String directory = "data";

    /**
     * @return the directory holding uploaded files, relative to the working directory unless absolute
     */
    public String directory() {
        return directory;
    }
}
