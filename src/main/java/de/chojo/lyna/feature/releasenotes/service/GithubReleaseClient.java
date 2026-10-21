/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasenotes.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.configuration.elements.Github;
import de.chojo.lyna.feature.releasenotes.entity.GithubRelease;
import de.chojo.lyna.feature.releasenotes.entity.GithubRepository;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Asks GitHub for a repository's releases.
 *
 * <p>Asks with the ETag of the last answer, so a repository that has not changed costs a 304, which
 * GitHub does not count against the rate limit. Drafts are left out; GitHub sends them only to a
 * token that may push, and they are not notes anybody should read yet.
 */
@Singleton
public class GithubReleaseClient {
    private static final Logger log = getLogger(GithubReleaseClient.class);

    /** The most releases kept for a repository: one page, as GitHub hands them out. */
    public static final int LIMIT = 100;

    private final Github github;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper json = new ObjectMapper();

    /**
     * What GitHub answered.
     */
    public sealed interface Answer {
        /** The releases have changed, or were never read: these are all of them. */
        record Releases(List<GithubRelease> releases, String etag) implements Answer {}

        /** Nothing changed since the ETag that was sent. */
        record Unchanged() implements Answer {}

        /** GitHub could not be asked, or refused. */
        record Failed() implements Answer {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Release(
            long id,
            @JsonProperty("tag_name") String tag,
            String name,
            String body,
            @JsonProperty("html_url") String url,
            boolean prerelease,
            boolean draft,
            @JsonProperty("published_at") String publishedAt) {}

    @Inject
    public GithubReleaseClient(Github github) {
        this.github = github;
    }

    /**
     * @param etag what GitHub answered with last time, or null when it was never asked
     */
    public Answer releases(GithubRepository repository, String etag) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("%s/repos/%s/%s/releases?per_page=%d"
                        .formatted(github.apiUrl(), repository.owner(), repository.name(), LIMIT)))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Lyna");
        if (!github.token().isBlank()) request.header("Authorization", "Bearer " + github.token());
        if (etag != null) request.header("If-None-Match", etag);
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 304) return new Answer.Unchanged();
            if (response.statusCode() != 200) {
                log.debug("GitHub answered {} for the releases of {}", response.statusCode(), repository);
                return new Answer.Failed();
            }
            List<Release> releases = json.readValue(response.body(), new TypeReference<>() {});
            return new Answer.Releases(
                    releases.stream()
                            .map(GithubReleaseClient::published)
                            .filter(Objects::nonNull)
                            .toList(),
                    response.headers().firstValue("ETag").orElse(null));
        } catch (IOException | IllegalArgumentException e) {
            log.debug("Could not fetch the releases of {}", repository, e);
            return new Answer.Failed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Answer.Failed();
        }
    }

    /**
     * @return the release as it is kept, or null for a draft or one GitHub gives no date for
     */
    private static GithubRelease published(Release release) {
        if (release.draft() || release.publishedAt() == null) return null;
        try {
            return new GithubRelease(
                    release.id(),
                    release.tag(),
                    release.name() == null || release.name().isBlank() ? null : release.name(),
                    release.body() == null || release.body().isBlank() ? null : release.body(),
                    release.url(),
                    release.prerelease(),
                    Instant.parse(release.publishedAt()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
