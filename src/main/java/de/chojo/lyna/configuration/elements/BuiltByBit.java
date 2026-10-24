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

import java.util.UUID;

/**
 * BuiltByBit's purchase webhook, its licence key placeholder, and the optional API behind both.
 *
 * <p>Neither call BuiltByBit makes is signed; a shared secret is the only thing saying a call is
 * theirs. The two secrets are separate because they travel differently: the webhook's sits in the
 * address BuiltByBit is given, the key placeholder's in the body of each request. Both default to
 * something random, so an instance nobody has configured refuses every call rather than accepting a
 * blank one.
 *
 * <p>The API and sign-in need BuiltByBit Ultimate. Left empty, Lyna works without them.
 */
@OverwritePrefix("BUILTBYBIT")
public class BuiltByBit {
    @Overwrite(env = @Env, prop = @Prop)
    private String webhookSecret = UUID.randomUUID().toString();

    @Overwrite(env = @Env, prop = @Prop)
    private String keySecret = UUID.randomUUID().toString();

    @Overwrite(env = @Env, prop = @Prop)
    private String apiToken = "";

    @Overwrite(env = @Env, prop = @Prop)
    private String apiUrl = "https://api.builtbybit.com";

    @Overwrite(env = @Env, prop = @Prop)
    private String oauthClientId = "";

    @Overwrite(env = @Env, prop = @Prop)
    private String oauthClientSecret = "";

    /**
     * @return the secret the purchase webhook carries in its address
     */
    public String webhookSecret() {
        return webhookSecret;
    }

    /**
     * @return the secret the licence key placeholder sends in its {@code secret} field
     */
    public String keySecret() {
        return keySecret;
    }

    /**
     * @return the API token, empty when there is none
     */
    public String apiToken() {
        return apiToken;
    }

    /**
     * @return whether an API token is configured, which needs BuiltByBit Ultimate
     */
    public boolean apiEnabled() {
        return !apiToken.isBlank();
    }

    public String apiUrl() {
        return apiUrl;
    }

    public String oauthClientId() {
        return oauthClientId;
    }

    public String oauthClientSecret() {
        return oauthClientSecret;
    }

    /**
     * @return whether sign-in with BuiltByBit is configured
     */
    public boolean oauthEnabled() {
        return !oauthClientId.isBlank() && !oauthClientSecret.isBlank();
    }
}
