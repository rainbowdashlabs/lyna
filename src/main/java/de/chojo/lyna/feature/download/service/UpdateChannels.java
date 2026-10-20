/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.service;

import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.util.Version;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Which build an installed one should update to.
 *
 * <p>A build only hears of the channels at or above its own: a stable build of stable builds, a dev
 * build of stable and dev builds, a snapshot build of all three. Somebody who installed a stable
 * release never wanted to be told about the dev build that came after it.
 *
 * <p>Dev and snapshot builds are overwritten under the same version when they are published again. For
 * those, the same version counts as an update when it was published after the installed build was made.
 */
public final class UpdateChannels {
    private static final Comparator<Build> NEWEST = Comparator.comparing(
                    (Build build) -> Version.parse(build.version()))
            .thenComparing(Build::published);

    private UpdateChannels() {}

    /**
     * @param version   the version as the repository names it
     * @param published when it was published
     */
    public record Build(String version, Instant published) {}

    /**
     * @param update whether {@code build} is one the installed build should update to
     * @param build  the newest build of the channels the installed one hears, if any channel has one
     */
    public record Decision(boolean update, Optional<Build> build) {}

    /**
     * @param current  the installed version
     * @param created  when the installed build was made, if the caller said; without it a rebuilt dev or
     *                 snapshot under the same version is not noticed
     * @param latestOf the newest build of a channel, empty when the product publishes none there
     */
    public static Decision decide(
            Version current, @Nullable Instant created, Function<ReleaseType, Optional<Build>> latestOf) {
        List<Build> heard = current.type().descendants().stream()
                .flatMap(type -> latestOf.apply(type).stream())
                .toList();
        Optional<Build> update = heard.stream()
                .filter(build -> isUpdate(current, created, build))
                .max(NEWEST);
        if (update.isPresent()) return new Decision(true, update);
        return new Decision(false, heard.stream().max(NEWEST));
    }

    private static boolean isUpdate(Version current, @Nullable Instant created, Build build) {
        Version candidate = Version.parse(build.version());
        if (candidate.isNewer(current)) return true;
        boolean rebuilt = candidate.type() != ReleaseType.STABLE
                && candidate.compareTo(current) == 0
                && created != null
                && created.isBefore(build.published());
        return rebuilt;
    }
}
