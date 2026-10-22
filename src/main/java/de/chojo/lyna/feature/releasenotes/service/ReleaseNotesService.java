/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasenotes.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.releasenotes.entity.GithubRelease;
import de.chojo.lyna.feature.releasenotes.entity.GithubRepository;
import de.chojo.lyna.feature.releasenotes.repository.GithubReleaseRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The release notes a product page shows, kept by Lyna and read from GitHub only when they are stale.
 *
 * <p>Two things keep them current. A page reading a repository whose copy is older than {@link #FRESH}
 * asks GitHub again, with the ETag of the last answer so an unchanged repository costs nothing. And a
 * product's release webhook writes each release in as GitHub reports it, so a product that has one does
 * not wait out the hour. When GitHub cannot be asked, the stored copy is served as it is.
 */
@Singleton
public class ReleaseNotesService {
    /** How long a repository's releases are served without asking GitHub again. */
    public static final Duration FRESH = Duration.ofHours(1);

    private static final Set<String> REMOVED = Set.of("deleted", "unpublished");

    private final GithubReleaseRepository repository;
    private final GithubReleaseClient client;

    @Inject
    public ReleaseNotesService(GithubReleaseRepository repository, GithubReleaseClient client) {
        this.repository = repository;
        this.client = client;
    }

    /**
     * @param projectUrl the product's project address
     * @return the releases of the GitHub repository it names, newest first; nothing when it names none
     */
    public List<GithubRelease> releases(String projectUrl) {
        Optional<GithubRepository> found = GithubRepository.of(projectUrl);
        if (found.isEmpty()) return List.of();
        refresh(found.get());
        return repository.releases(found.get(), GithubReleaseClient.LIMIT);
    }

    private void refresh(GithubRepository target) {
        if (repository.fresh(target, FRESH)) return;
        switch (client.releases(target, repository.etag(target).orElse(null))) {
            case GithubReleaseClient.Answer.Releases answer ->
                repository.replace(target, answer.releases(), answer.etag());
            case GithubReleaseClient.Answer.Unchanged ignored -> repository.touch(target);
            case GithubReleaseClient.Answer.Failed ignored -> {}
        }
    }

    /**
     * Takes in a release a product's webhook reported.
     *
     * <p>Only a release of the repository the product names is kept: the webhook is the product's, and
     * whatever repository it is installed on, it writes nothing for any other. A deleted, unpublished or
     * draft release is taken out; anything else is written as it now is.
     *
     * @param projectUrl         the product's project address
     * @param repositoryFullName {@code owner/name} of the repository the event came from
     * @param action             GitHub's action, such as {@code published}, {@code edited} or {@code deleted}
     * @param releaseId          GitHub's id for the release
     * @param draft              whether the release is a draft
     * @param release            the release as it is kept, or null when it has no publication date
     */
    public void received(
            String projectUrl,
            String repositoryFullName,
            String action,
            long releaseId,
            boolean draft,
            GithubRelease release) {
        Optional<GithubRepository> found = GithubRepository.of(projectUrl);
        if (found.isEmpty() || !found.get().is(repositoryFullName)) return;
        if (draft || REMOVED.contains(action)) {
            repository.delete(found.get(), releaseId);
            return;
        }
        if (release != null) repository.upsert(found.get(), release);
    }
}
