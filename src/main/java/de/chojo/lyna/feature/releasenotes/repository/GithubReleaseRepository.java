/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasenotes.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.releasenotes.entity.GithubRelease;
import de.chojo.lyna.feature.releasenotes.entity.GithubRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The releases of GitHub repositories as Lyna keeps them, and when each repository was last read.
 *
 * <p>Every write is one statement, so a page never sees a repository half replaced.
 */
@Singleton
public class GithubReleaseRepository {
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @return the ETag GitHub last answered with, when the repository has been read before
     */
    public Optional<String> etag(GithubRepository repository) {
        return query("SELECT etag FROM github_release_sync WHERE owner = ? AND repository = ?")
                .single(call().bind(repository.owner()).bind(repository.name()))
                .map(row -> row.getString("etag"))
                .first();
    }

    /**
     * @return whether the repository was read from GitHub within the given time
     */
    public boolean fresh(GithubRepository repository, Duration within) {
        return query("""
                SELECT exists(
                    SELECT 1 FROM github_release_sync
                    WHERE owner = ? AND repository = ? AND fetched_at > now() - make_interval(secs => ?)
                ) AS fresh
                """)
                .single(call().bind(repository.owner()).bind(repository.name()).bind(within.toSeconds()))
                .map(row -> row.getBoolean("fresh"))
                .first()
                .orElse(false);
    }

    /**
     * @return the repository's releases, newest first
     */
    public List<GithubRelease> releases(GithubRepository repository, int limit) {
        return query("""
                SELECT release_id, tag, name, body, html_url, prerelease, published_at
                FROM github_release
                WHERE owner = ? AND repository = ?
                ORDER BY published_at DESC, release_id DESC
                LIMIT ?
                """)
                .single(call().bind(repository.owner()).bind(repository.name()).bind(limit))
                .map(row -> new GithubRelease(
                        row.getLong("release_id"),
                        row.getString("tag"),
                        row.getString("name"),
                        row.getString("body"),
                        row.getString("html_url"),
                        row.getBoolean("prerelease"),
                        row.getTimestamp("published_at").toInstant()))
                .all();
    }

    /**
     * Replaces everything stored for the repository with what GitHub just answered, and records that
     * it was read now.
     *
     * @param etag what GitHub answered with, to ask next time whether anything changed
     */
    public void replace(GithubRepository repository, List<GithubRelease> releases, String etag) {
        query("""
                WITH sync AS (
                    INSERT INTO github_release_sync (owner, repository, fetched_at, etag)
                    VALUES (?, ?, now(), ?)
                    ON CONFLICT (owner, repository) DO UPDATE SET fetched_at = now(), etag = excluded.etag
                ),
                incoming AS (
                    SELECT * FROM jsonb_to_recordset(?::jsonb) AS r(
                        id BIGINT, tag TEXT, name TEXT, body TEXT, url TEXT, prerelease BOOLEAN, published_at TIMESTAMPTZ)
                ),
                gone AS (
                    DELETE FROM github_release
                    WHERE owner = ? AND repository = ? AND release_id NOT IN (SELECT id FROM incoming)
                )
                INSERT INTO github_release (owner, repository, release_id, tag, name, body, html_url, prerelease, published_at)
                SELECT ?, ?, id, tag, name, body, url, prerelease, published_at FROM incoming
                ON CONFLICT (owner, repository, release_id) DO UPDATE SET
                    tag = excluded.tag, name = excluded.name, body = excluded.body, html_url = excluded.html_url,
                    prerelease = excluded.prerelease, published_at = excluded.published_at
                """)
                .single(call().bind(repository.owner())
                        .bind(repository.name())
                        .bind(etag)
                        .bind(asJson(releases))
                        .bind(repository.owner())
                        .bind(repository.name())
                        .bind(repository.owner())
                        .bind(repository.name()))
                .insert();
    }

    /**
     * Records that GitHub was asked just now and had nothing new.
     */
    public void touch(GithubRepository repository) {
        query("UPDATE github_release_sync SET fetched_at = now() WHERE owner = ? AND repository = ?")
                .single(call().bind(repository.owner()).bind(repository.name()))
                .update();
    }

    /**
     * Stores one release as the webhook reported it, leaving the rest of the repository alone.
     *
     * <p>A repository never read before is recorded as read at the beginning of time, so the first page
     * that asks still reads every other release from GitHub.
     */
    public void upsert(GithubRepository repository, GithubRelease release) {
        query("""
                WITH sync AS (
                    INSERT INTO github_release_sync (owner, repository, fetched_at)
                    VALUES (?, ?, '-infinity')
                    ON CONFLICT (owner, repository) DO NOTHING
                )
                INSERT INTO github_release (owner, repository, release_id, tag, name, body, html_url, prerelease, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner, repository, release_id) DO UPDATE SET
                    tag = excluded.tag, name = excluded.name, body = excluded.body, html_url = excluded.html_url,
                    prerelease = excluded.prerelease, published_at = excluded.published_at
                """)
                .single(call().bind(repository.owner())
                        .bind(repository.name())
                        .bind(repository.owner())
                        .bind(repository.name())
                        .bind(release.id())
                        .bind(release.tag())
                        .bind(release.name())
                        .bind(release.body())
                        .bind(release.url())
                        .bind(release.prerelease())
                        .bind(Timestamp.from(release.publishedAt())))
                .insert();
    }

    public void delete(GithubRepository repository, long releaseId) {
        query("DELETE FROM github_release WHERE owner = ? AND repository = ? AND release_id = ?")
                .single(call().bind(repository.owner()).bind(repository.name()).bind(releaseId))
                .delete();
    }

    /**
     * A release as the replacing statement reads it out of its JSON parameter.
     */
    private record Incoming(
            long id,
            String tag,
            String name,
            String body,
            String url,
            boolean prerelease,
            @JsonProperty("published_at") String publishedAt) {}

    private String asJson(List<GithubRelease> releases) {
        try {
            return json.writeValueAsString(releases.stream()
                    .map(release -> new Incoming(
                            release.id(),
                            release.tag(),
                            release.name(),
                            release.body(),
                            release.url(),
                            release.prerelease(),
                            release.publishedAt().toString()))
                    .toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Releases could not be written as JSON", e);
        }
    }
}
