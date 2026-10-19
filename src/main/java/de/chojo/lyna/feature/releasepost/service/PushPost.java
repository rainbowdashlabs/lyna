/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasepost.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;
import java.util.Optional;

/**
 * A push to a repository's default branch, as its commits.
 *
 * <p>Only the default branch: feature branches come and go many times a day, and a channel that
 * posts each of them is a channel nobody reads. Tags and deleted branches are not commits anybody
 * wrote, so they are not posted either.
 */
public final class PushPost {
    /** How many commits a post lists before it says how many more there were. */
    public static final int COMMIT_LIMIT = 5;

    private static final String BRANCH_PREFIX = "refs/heads/";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String ref, boolean deleted, String compare, List<Commit> commits, Repository repository) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Commit(String id, String message, String url, Author author) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(String name, String username) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(
            @JsonProperty("full_name") String fullName,
            @JsonProperty("default_branch") String defaultBranch) {}

    private PushPost() {}

    /**
     * @return the post, or nothing when the push is not one to the default branch with commits in it
     */
    public static Optional<MessageEmbed> embed(String product, Payload payload) {
        if (payload.deleted() || payload.ref() == null || payload.repository() == null) return Optional.empty();
        String branch = payload.repository().defaultBranch();
        if (branch == null || !payload.ref().equals(BRANCH_PREFIX + branch)) return Optional.empty();
        List<Commit> commits = payload.commits() == null ? List.of() : payload.commits();
        if (commits.isEmpty()) return Optional.empty();

        String title = commits.size() == 1
                ? "1 new commit to %s".formatted(branch)
                : "%d new commits to %s".formatted(commits.size(), branch);
        StringBuilder lines = new StringBuilder();
        for (Commit commit : commits.subList(0, Math.min(COMMIT_LIMIT, commits.size()))) {
            lines.append(line(commit)).append('\n');
        }
        if (commits.size() > COMMIT_LIMIT) {
            lines.append("and %d more".formatted(commits.size() - COMMIT_LIMIT));
        }
        return Optional.of(GithubEmbeds.base(
                        product,
                        title,
                        payload.compare(),
                        GithubEmbeds.PUSH,
                        payload.repository().fullName())
                .setDescription(GithubEmbeds.cut(lines.toString(), MessageEmbed.DESCRIPTION_MAX_LENGTH))
                .build());
    }

    private static String line(Commit commit) {
        String id = commit.id() == null
                ? ""
                : commit.id().substring(0, Math.min(7, commit.id().length()));
        String hash = commit.url() == null ? "`%s`".formatted(id) : "[`%s`](%s)".formatted(id, commit.url());
        String subject = GithubEmbeds.cut(GithubEmbeds.firstLine(commit.message()), 100);
        return commit.author() == null || commit.author().name() == null
                ? "%s %s".formatted(hash, subject)
                : "%s %s - %s".formatted(hash, subject, commit.author().name());
    }
}
