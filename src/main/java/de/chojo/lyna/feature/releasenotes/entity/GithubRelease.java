/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasenotes.entity;

import java.time.Instant;

/**
 * A published release of a GitHub repository.
 *
 * @param id   GitHub's id for it, which stays the same when the release is edited
 * @param name its title, or null when it has none and goes by its tag
 * @param body its notes, as markdown, or null when it has none
 * @param url  its page on GitHub
 */
public record GithubRelease(
        long id, String tag, String name, String body, String url, boolean prerelease, Instant publishedAt) {}
