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
 * Reading from GitHub, which product pages do to show a project's README.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("GITHUB")
public class Github {
    /**
     * A token for the GitHub API. Optional: without one GitHub allows sixty requests an hour from this
     * address, which READMEs cached for an hour rarely reach. Supply it as {@code GITHUB_TOKEN}.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private String token = "";

    /**
     * Where the GitHub API is. Changed only to point tests at a stand-in.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private String apiUrl = "https://api.github.com";

    public String token() {
        return token;
    }

    public String apiUrl() {
        return apiUrl;
    }
}
