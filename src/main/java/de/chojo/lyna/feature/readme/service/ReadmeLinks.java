/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.readme.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a README's relative links lead once it is shown away from GitHub.
 *
 * <p>On GitHub {@code docs/setup.md} and {@code img/logo.png} resolve against the README's own folder.
 * On a product page they would resolve against the page, so they are made absolute: images to
 * {@code raw.githubusercontent.com}, which serves the file itself, and everything else to the file's
 * page on GitHub. Absolute links and anchors are left alone.
 */
public final class ReadmeLinks {
    private static final Pattern REPOSITORY = Pattern.compile(
            "^https?://(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+?)(?:\\.git)?/?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("(!\\[[^\\]]*]\\()([^)\\s]+)");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("((?<!!)\\[[^\\]]*]\\()([^)\\s]+)");
    private static final Pattern HTML_SRC =
            Pattern.compile("(<img\\b[^>]*?\\bsrc=[\"'])([^\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_HREF =
            Pattern.compile("(<a\\b[^>]*?\\bhref=[\"'])([^\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_TARGET =
            Pattern.compile("(\\]\\(|href=[\"'])(https?://[^)\"'\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLUGIN_HOST = Pattern.compile(
            "^https?://(?:www\\.)?(?:spigotmc\\.org/resources|modrinth\\.com/(?:plugin|mod)|hangar\\.papermc\\.io|curseforge\\.com)/.*",
            Pattern.CASE_INSENSITIVE);

    /**
     * @param owner the account the repository belongs to
     * @param name  the repository
     */
    public record Repository(String owner, String name) {}

    private ReadmeLinks() {}

    /**
     * @return the repository a project address names, when it is a GitHub repository's page
     */
    public static Optional<Repository> repository(String url) {
        if (url == null) return Optional.empty();
        Matcher matcher = REPOSITORY.matcher(url.strip());
        return matcher.matches() ? Optional.of(new Repository(matcher.group(1), matcher.group(2))) : Optional.empty();
    }

    /**
     * @param path the README's path in the repository, such as {@code README.md} or {@code docs/README.md}
     */
    public static String absolute(String markdown, Repository repository, String path) {
        String folder = path.contains("/") ? path.substring(0, path.lastIndexOf('/') + 1) : "";
        String raw = "https://raw.githubusercontent.com/%s/%s/HEAD/".formatted(repository.owner(), repository.name());
        String blob = "https://github.com/%s/%s/blob/HEAD/".formatted(repository.owner(), repository.name());
        String result = rewrite(markdown, MARKDOWN_IMAGE, raw, folder);
        result = rewrite(result, HTML_SRC, raw, folder);
        result = rewrite(result, MARKDOWN_LINK, blob, folder);
        return rewrite(result, HTML_HREF, blob, folder);
    }

    /**
     * Sends a README's download links to Lyna: links to the repository's releases and to the usual
     * plugin hosts lead to {@code target} instead. Badge images stay what they are; only where they
     * lead changes.
     */
    public static String downloadsTo(String markdown, Repository repository, String target) {
        String releases = "https://github.com/%s/%s/releases"
                .formatted(repository.owner(), repository.name())
                .toLowerCase(Locale.ROOT);
        Matcher matcher = LINK_TARGET.matcher(markdown);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String url = matcher.group(2);
            boolean download = url.toLowerCase(Locale.ROOT).startsWith(releases)
                    || PLUGIN_HOST.matcher(url).matches();
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + (download ? target : url)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * @param root   the repository's root, under which a link starting with {@code /} resolves
     * @param folder the README's folder, under which any other relative link resolves
     */
    private static String rewrite(String text, Pattern pattern, String root, String folder) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(
                    out, Matcher.quoteReplacement(matcher.group(1) + resolve(matcher.group(2), root, folder)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String resolve(String target, String root, String folder) {
        if (target.startsWith("#") || target.startsWith("//") || target.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))
            return target;
        if (target.startsWith("/")) return root + target.substring(1);
        return root + folder + target.replaceFirst("^\\./", "");
    }
}
