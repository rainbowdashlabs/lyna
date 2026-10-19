/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasepost.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.chojo.lyna.feature.releasepost.repository.ReleaseWebhookRepository.Activity;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.Optional;

/**
 * A pull request opened or merged. One closed without merging, and every edit, label and review in
 * between, is not posted: what a channel wants to know is that work was proposed and that it landed.
 */
public final class PullRequestPost {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            String action,
            int number,
            @JsonProperty("pull_request") PullRequest pullRequest,
            PushPost.Repository repository) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(
            String title,
            @JsonProperty("html_url") String url,
            String body,
            boolean merged,
            User user,
            @JsonProperty("merged_by") User mergedBy) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(String login) {}

    private PullRequestPost() {}

    /**
     * @return the post, or nothing for an action that is not posted or that the product switched off
     */
    public static Optional<MessageEmbed> embed(String product, Payload payload, Activity activity) {
        PullRequest pr = payload.pullRequest();
        if (pr == null) return Optional.empty();
        String repository =
                payload.repository() == null ? null : payload.repository().fullName();
        if ("opened".equals(payload.action()) && activity.prOpened()) {
            return Optional.of(GithubEmbeds.base(
                            product,
                            "PR #%d opened: %s".formatted(payload.number(), pr.title()),
                            pr.url(),
                            GithubEmbeds.OPENED,
                            repository)
                    .setDescription(GithubEmbeds.cut(pr.body(), GithubEmbeds.BODY_LIMIT))
                    .addField("By", login(pr.user()), true)
                    .build());
        }
        if ("closed".equals(payload.action()) && pr.merged() && activity.prMerged()) {
            return Optional.of(GithubEmbeds.base(
                            product,
                            "PR #%d merged: %s".formatted(payload.number(), pr.title()),
                            pr.url(),
                            GithubEmbeds.MERGED,
                            repository)
                    .addField("By", login(pr.user()), true)
                    .addField("Merged by", login(pr.mergedBy()), true)
                    .build());
        }
        return Optional.empty();
    }

    static String login(User user) {
        return user == null || user.login() == null ? "unknown" : user.login();
    }
}
