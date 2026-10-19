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
 * An issue opened or closed. Closed says why, since "not planned" and "completed" are different news
 * to whoever reported it.
 */
public final class IssuePost {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String action, Issue issue, PushPost.Repository repository) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issue(
            int number,
            String title,
            @JsonProperty("html_url") String url,
            String body,
            PullRequestPost.User user,
            @JsonProperty("state_reason") String stateReason) {}

    private IssuePost() {}

    /**
     * @return the post, or nothing for an action that is not posted or that the product switched off
     */
    public static Optional<MessageEmbed> embed(String product, Payload payload, Activity activity) {
        Issue issue = payload.issue();
        if (issue == null) return Optional.empty();
        String repository =
                payload.repository() == null ? null : payload.repository().fullName();
        if ("opened".equals(payload.action()) && activity.issueOpened()) {
            return Optional.of(GithubEmbeds.base(
                            product,
                            "Issue #%d opened: %s".formatted(issue.number(), issue.title()),
                            issue.url(),
                            GithubEmbeds.OPENED,
                            repository)
                    .setDescription(GithubEmbeds.cut(issue.body(), GithubEmbeds.BODY_LIMIT))
                    .addField("By", PullRequestPost.login(issue.user()), true)
                    .build());
        }
        if ("closed".equals(payload.action()) && activity.issueClosed()) {
            return Optional.of(GithubEmbeds.base(
                            product,
                            "Issue #%d closed: %s".formatted(issue.number(), issue.title()),
                            issue.url(),
                            GithubEmbeds.CLOSED,
                            repository)
                    .addField("Reason", reason(issue.stateReason()), true)
                    .build());
        }
        return Optional.empty();
    }

    private static String reason(String stateReason) {
        if ("not_planned".equals(stateReason)) return "Not planned";
        if ("duplicate".equals(stateReason)) return "Duplicate";
        return "Completed";
    }
}
