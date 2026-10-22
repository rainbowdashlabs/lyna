/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.releasenotes.entity.GithubRelease;
import de.chojo.lyna.feature.releasenotes.entity.GithubRepository;
import de.chojo.lyna.feature.releasenotes.repository.GithubReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubReleaseRepositoryTest extends RepositoryTestBase {
    private static final GithubRepository REPOSITORY = new GithubRepository("Owner", "Repo");
    private static final GithubRepository OTHER = new GithubRepository("owner", "other");

    private final GithubReleaseRepository releases = new GithubReleaseRepository();

    @BeforeEach
    void seed() throws SQLException {
        clear("github_release", "github_release_sync");
    }

    private static GithubRelease release(long id, String name, String body, String published) {
        return new GithubRelease(
                id,
                "v" + id,
                name,
                body,
                "https://github.com/owner/repo/releases/tag/v" + id,
                id % 2 == 0,
                Instant.parse(published));
    }

    @Test
    @DisplayName("A repository never read is neither fresh nor has an ETag or releases")
    void unknown() {
        assertFalse(releases.fresh(REPOSITORY, Duration.ofHours(1)));
        assertTrue(releases.etag(REPOSITORY).isEmpty());
        assertTrue(releases.releases(REPOSITORY, 10).isEmpty());
    }

    @Test
    @DisplayName("Replacing stores the releases newest first, keeps nulls, and leaves other repositories alone")
    void replace() {
        var older = release(1, null, null, "2026-10-01T10:00:00Z");
        var newer = release(2, "Second", "**notes**", "2026-10-02T10:00:00Z");
        releases.replace(OTHER, List.of(older), null);

        releases.replace(REPOSITORY, List.of(older, newer), "\"e1\"");

        assertEquals(List.of(newer, older), releases.releases(REPOSITORY, 10));
        assertEquals(List.of(newer), releases.releases(REPOSITORY, 1));
        assertEquals("\"e1\"", releases.etag(REPOSITORY).orElseThrow());
        assertTrue(releases.fresh(REPOSITORY, Duration.ofHours(1)));

        releases.replace(REPOSITORY, List.of(newer), "\"e2\"");
        assertEquals(List.of(newer), releases.releases(REPOSITORY, 10));
        assertEquals(List.of(older), releases.releases(OTHER, 10));
    }

    @Test
    @DisplayName("Touching marks a stale repository as just read")
    void touch() throws SQLException {
        releases.replace(REPOSITORY, List.of(), "\"e1\"");
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "UPDATE %s.github_release_sync SET fetched_at = now() - interval '2 hours'".formatted(schemaName));
        }
        assertFalse(releases.fresh(REPOSITORY, Duration.ofHours(1)));

        releases.touch(REPOSITORY);

        assertTrue(releases.fresh(REPOSITORY, Duration.ofHours(1)));
    }

    @Test
    @DisplayName("One release is written in and taken out without the repository counting as read")
    void upsertAndDelete() {
        var release = release(5, "Five", "notes", "2026-10-05T10:00:00Z");

        releases.upsert(REPOSITORY, release);
        releases.upsert(REPOSITORY, release);

        assertEquals(List.of(release), releases.releases(REPOSITORY, 10));
        assertFalse(releases.fresh(REPOSITORY, Duration.ofHours(1)));

        releases.delete(REPOSITORY, 5);
        assertTrue(releases.releases(REPOSITORY, 10).isEmpty());
    }
}
