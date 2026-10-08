/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.readme.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.configuration.elements.Github;
import de.chojo.lyna.feature.readme.service.ReadmeLinks.Repository;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * A GitHub repository's README, for a product page to show.
 *
 * <p>Kept for an hour, so a busy product page asks GitHub a few times a day rather than once a visit.
 * When GitHub cannot be reached, or limits this address, the last README fetched is served again: a
 * product page should not go blank because GitHub had a bad minute.
 */
@Singleton
public class ReadmeService {
    private static final Logger log = getLogger(ReadmeService.class);

    private final Github github;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final Cache<Repository, Readme> fresh = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(500)
            .build();
    private final Map<Repository, Readme> lastGood = new ConcurrentHashMap<>();

    /**
     * @param markdown the README with its relative links made absolute
     * @param url      the README's page on GitHub
     */
    public record Readme(String markdown, String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Contents(
            String content,
            String encoding,
            String path,
            @JsonProperty("html_url") String htmlUrl) {}

    @Inject
    public ReadmeService(Github github) {
        this.github = github;
    }

    /**
     * @return the README of the repository a project address names, or nothing when it names none or
     *         GitHub has never answered for it
     */
    public Optional<Readme> readme(String projectUrl) {
        return ReadmeLinks.repository(projectUrl).flatMap(this::readme);
    }

    private Optional<Readme> readme(Repository repository) {
        Readme cached = fresh.getIfPresent(repository);
        if (cached != null) return Optional.of(cached);
        Optional<Readme> fetched = fetch(repository);
        fetched.ifPresent(readme -> {
            fresh.put(repository, readme);
            lastGood.put(repository, readme);
        });
        return fetched.or(() -> Optional.ofNullable(lastGood.get(repository)));
    }

    private Optional<Readme> fetch(Repository repository) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(
                        "%s/repos/%s/%s/readme".formatted(github.apiUrl(), repository.owner(), repository.name())))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Lyna");
        if (!github.token().isBlank()) request.header("Authorization", "Bearer " + github.token());
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("GitHub answered {} for the README of {}", response.statusCode(), repository);
                return Optional.empty();
            }
            Contents contents = json.readValue(response.body(), Contents.class);
            if (contents.content() == null || !"base64".equals(contents.encoding())) return Optional.empty();
            String markdown = new String(Base64.getMimeDecoder().decode(contents.content()), StandardCharsets.UTF_8);
            return Optional.of(new Readme(
                    ReadmeLinks.absolute(markdown, repository, contents.path() == null ? "README.md" : contents.path()),
                    contents.htmlUrl()));
        } catch (IOException | IllegalArgumentException e) {
            log.debug("Could not fetch the README of {}", repository, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
