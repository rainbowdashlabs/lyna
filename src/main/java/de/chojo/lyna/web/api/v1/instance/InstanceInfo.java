/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.instance;

import com.google.inject.Inject;
import de.chojo.lyna.configuration.elements.Links;
import io.javalin.http.Context;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

/**
 * What an instance says about itself to anybody who asks.
 *
 * <p>Public on purpose: the footer is drawn on the storefront and the sign-in page, where nobody has
 * a session yet. Nothing here is a secret - the build it is running and the links it was configured
 * with are both things a visitor is meant to see.
 */
public class InstanceInfo {
    private final Links links;

    @Inject
    public InstanceInfo(Links links) {
        this.links = links;
    }

    public void init() {
        path("instance", () -> get(this::info));
    }

    private void info(Context ctx) {
        ctx.json(new Info(
                version(),
                blankToNull(links.website()),
                blankToNull(links.support()),
                blankToNull(links.invite()),
                blankToNull(links.faq()),
                blankToNull(links.tos())));
    }

    /**
     * @return what this build is, carrying the commit and the moment it was built when CI made it
     */
    private String version() {
        try (var in = getClass().getResourceAsStream("/version")) {
            return in == null ? "unknown" : new String(in.readAllBytes()).trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** A link nobody configured is absent rather than empty, so the footer can leave it out. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * @param version what this instance is running
     * @param discord where to find the people who run it, when they said
     * @param invite  where to add the bot, when there is a bot to add
     */
    private record Info(String version, String website, String discord, String invite, String faq, String terms) {}
}
