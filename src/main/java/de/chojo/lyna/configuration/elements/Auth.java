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
 * How sessions are signed and how long they last.
 *
 * <p>A session runs out after {@link #sessionDays()} without being used. Its token is re-signed once
 * it is older than {@link #renewAfterHours()}, and that renewal is what moves the end along.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("AUTH")
public class Auth {
    @Overwrite(env = @Env, prop = @Prop)
    private String jwtSecret = "";

    @Overwrite(env = @Env, prop = @Prop)
    private int sessionDays = 30;

    @Overwrite(env = @Env, prop = @Prop)
    private int renewAfterHours = 24;

    /**
     * The key stored Discord tokens are encrypted with, base64. Empty means the key file beside the
     * configuration, which is made on first start. Supply it as {@code AUTH_TOKENKEY} to keep it out
     * of files altogether.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private String tokenKey = "";

    public String jwtSecret() {
        return jwtSecret;
    }

    public int sessionDays() {
        return sessionDays;
    }

    public int renewAfterHours() {
        return renewAfterHours;
    }

    public String tokenKey() {
        return tokenKey;
    }
}
