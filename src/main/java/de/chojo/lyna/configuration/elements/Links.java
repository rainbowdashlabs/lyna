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
@OverwritePrefix("LINKS")
public class Links {
    @Overwrite(env = @Env, prop = @Prop)
    private String tos = "";

    @Overwrite(env = @Env, prop = @Prop)
    private String invite =
            "https://discord.com/oauth2/authorize?client_id=1065674230362017813&scope=bot&permissions=2415921152";

    @Overwrite(env = @Env, prop = @Prop)
    private String support = "";

    @Overwrite(env = @Env, prop = @Prop)
    private String website = "";

    @Overwrite(env = @Env, prop = @Prop)
    private String faq = "";

    @Overwrite(env = @Env, prop = @Prop)
    private String frontend = "http://localhost:3000";

    public String tos() {
        return tos;
    }

    public String frontend() {
        return frontend;
    }

    public String invite() {
        return invite;
    }

    public String support() {
        return support;
    }

    public String website() {
        return website;
    }

    public String faq() {
        return faq;
    }
}
