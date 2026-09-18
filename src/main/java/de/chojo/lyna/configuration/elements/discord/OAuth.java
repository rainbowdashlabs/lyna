/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.configuration.elements.discord;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;
import dev.chojo.ocular.override.Prop;

/**
 * What Discord needs to hand an account back after somebody signs in there.
 *
 * <p>Its own class rather than one nested in {@code Discord}, because Ocular's annotation processor
 * assumes an annotated class sits directly in a package and fails to compile one that does not.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("DISCORD_OAUTH")
public class OAuth {
    @Overwrite(env = @Env, prop = @Prop)
    private String clientId = "";

    /** Supplied from outside the file wherever that is possible. */
    @Overwrite(env = @Env, prop = @Prop)
    private String clientSecret = "";

    @Overwrite(env = @Env, prop = @Prop)
    private String redirectUri = "";

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public String redirectUri() {
        return redirectUri;
    }

    /**
     * Whether Discord can actually be signed in with.
     *
     * <p>All three or none: an authorize URL built from a blank client id is one Discord refuses,
     * and it refuses it on its own page, where nothing here can explain why. An instance that does
     * not want Discord sign-in leaves these empty and the endpoints say so.
     */
    public boolean configured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }
}
