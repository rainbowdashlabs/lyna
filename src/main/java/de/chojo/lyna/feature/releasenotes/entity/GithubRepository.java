/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasenotes.entity;

import de.chojo.lyna.feature.readme.service.ReadmeLinks;

import java.util.Locale;
import java.util.Optional;

/**
 * A GitHub repository as its releases are stored under: in lower case, because GitHub does not tell
 * {@code Owner/Repo} and {@code owner/repo} apart and product addresses are typed either way.
 */
public record GithubRepository(String owner, String name) {
    public GithubRepository {
        owner = owner.toLowerCase(Locale.ROOT);
        name = name.toLowerCase(Locale.ROOT);
    }

    /**
     * @return the repository a project address names, or nothing when it names none
     */
    public static Optional<GithubRepository> of(String projectUrl) {
        return ReadmeLinks.repository(projectUrl).map(found -> new GithubRepository(found.owner(), found.name()));
    }

    /**
     * @param fullName {@code owner/name}, as GitHub writes it in a webhook payload
     * @return whether that is this repository
     */
    public boolean is(String fullName) {
        return fullName != null && fullName.equalsIgnoreCase(owner + "/" + name);
    }
}
