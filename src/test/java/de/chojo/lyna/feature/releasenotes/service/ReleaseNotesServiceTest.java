/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasenotes.service;

import de.chojo.lyna.feature.releasenotes.entity.GithubRelease;
import de.chojo.lyna.feature.releasenotes.entity.GithubRepository;
import de.chojo.lyna.feature.releasenotes.repository.GithubReleaseRepository;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Release notes served from Lyna's copy, read from GitHub only when that copy is stale, and written by
 * the release webhook.
 */
class ReleaseNotesServiceTest extends RepositoryTestBase {
    private static final String URL = "https://github.com/Owner/Repo";
    private static final GithubRepository REPOSITORY = new GithubRepository("owner", "repo");
    private static final GithubRelease FIRST = release(1, "v1", "2026-10-01T10:00:00Z");
    private static final GithubRelease SECOND = release(2, "v2", "2026-10-02T10:00:00Z");

    private final GithubReleaseRepository releases = new GithubReleaseRepository();
    private GithubReleaseClient client;
    private ReleaseNotesService service;

    @BeforeEach
    void setUp() throws SQLException {
        clear("github_release", "github_release_sync");
        client = mock(GithubReleaseClient.class);
        service = new ReleaseNotesService(releases, client);
    }

    private static GithubRelease release(long id, String tag, String published) {
        return new GithubRelease(
                id,
                tag,
                "Release " + tag,
                "Notes of " + tag,
                "https://github.com/owner/repo/releases/tag/" + tag,
                false,
                Instant.parse(published));
    }

    private void age() throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "UPDATE %s.github_release_sync SET fetched_at = now() - interval '2 hours'".formatted(schemaName));
        }
    }

    @Test
    @DisplayName("A product that names no GitHub repository has no release notes, and GitHub is not asked")
    void noRepository() {
        assertTrue(service.releases("https://example.invalid/project").isEmpty());
        assertTrue(service.releases(null).isEmpty());
        verify(client, never()).releases(any(), any());
    }

    @Test
    @DisplayName("The first read asks GitHub and keeps the answer; a fresh copy is served without asking")
    void firstReadThenFresh() {
        when(client.releases(REPOSITORY, null))
                .thenReturn(new GithubReleaseClient.Answer.Releases(List.of(SECOND, FIRST), "\"e1\""));

        assertEquals(List.of(SECOND, FIRST), service.releases(URL));
        assertEquals(List.of(SECOND, FIRST), service.releases(URL));

        verify(client, times(1)).releases(any(), any());
    }

    @Test
    @DisplayName("A stale copy asks again with the last ETag; unchanged keeps it, new releases replace it")
    void staleAsksAgain() throws SQLException {
        when(client.releases(REPOSITORY, null))
                .thenReturn(new GithubReleaseClient.Answer.Releases(List.of(SECOND, FIRST), "\"e1\""));
        service.releases(URL);

        age();
        when(client.releases(REPOSITORY, "\"e1\"")).thenReturn(new GithubReleaseClient.Answer.Unchanged());
        assertEquals(List.of(SECOND, FIRST), service.releases(URL));
        assertTrue(releases.fresh(REPOSITORY, ReleaseNotesService.FRESH), "a 304 counts as having asked");

        age();
        GithubRelease third = release(3, "v3", "2026-10-03T10:00:00Z");
        when(client.releases(REPOSITORY, "\"e1\""))
                .thenReturn(new GithubReleaseClient.Answer.Releases(List.of(third, SECOND), "\"e2\""));
        assertEquals(List.of(third, SECOND), service.releases(URL), "a release GitHub no longer has is gone");
        assertEquals("\"e2\"", releases.etag(REPOSITORY).orElseThrow());
    }

    @Test
    @DisplayName("When GitHub cannot be asked, what is kept is served as it is")
    void failureKeepsCopy() throws SQLException {
        when(client.releases(REPOSITORY, null))
                .thenReturn(new GithubReleaseClient.Answer.Releases(List.of(FIRST), "\"e1\""));
        service.releases(URL);
        age();
        when(client.releases(REPOSITORY, "\"e1\"")).thenReturn(new GithubReleaseClient.Answer.Failed());

        assertEquals(List.of(FIRST), service.releases(URL));
        assertTrue(!releases.fresh(REPOSITORY, ReleaseNotesService.FRESH), "a failure is asked again next time");
    }

    @Test
    @DisplayName("The webhook writes a release in, edits it, and takes it out again")
    void webhook() {
        when(client.releases(REPOSITORY, null)).thenReturn(new GithubReleaseClient.Answer.Failed());

        service.received(URL, "owner/repo", "published", 1, false, FIRST);
        assertEquals(List.of(FIRST), service.releases(URL), "a repository first seen by the webhook still asks GitHub");

        GithubRelease edited =
                new GithubRelease(1, "v1", "Renamed", "New notes", FIRST.url(), true, FIRST.publishedAt());
        service.received(URL, "Owner/Repo", "edited", 1, false, edited);
        assertEquals(List.of(edited), service.releases(URL));

        service.received(URL, "owner/repo", "deleted", 1, false, edited);
        assertTrue(service.releases(URL).isEmpty());
    }

    @Test
    @DisplayName("Drafts, undated releases and other repositories' releases are not kept")
    void webhookIgnores() {
        when(client.releases(REPOSITORY, null)).thenReturn(new GithubReleaseClient.Answer.Failed());
        service.received(URL, "owner/repo", "published", 1, false, FIRST);

        service.received(URL, "owner/repo", "edited", 1, true, FIRST);
        assertTrue(service.releases(URL).isEmpty(), "a release turned back into a draft is taken out");

        service.received(URL, "owner/repo", "created", 2, false, null);
        service.received(URL, "somebody/else", "published", 3, false, release(3, "v3", "2026-10-03T10:00:00Z"));
        service.received("https://example.invalid/project", "owner/repo", "published", 4, false, FIRST);
        assertTrue(service.releases(URL).isEmpty());
    }
}
