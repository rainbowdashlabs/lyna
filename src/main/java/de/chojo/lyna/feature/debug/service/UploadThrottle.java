/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.configuration.elements.DebugReports;

import java.time.Duration;

/**
 * Lets an address upload one debug report per interval.
 *
 * <p>Uploads need no sign-in, since the plugins sending them have none, so this is the only thing
 * between an open endpoint and a database filled with logs.
 */
@Singleton
public class UploadThrottle {
    private final Cache<String, Boolean> recent;

    @Inject
    public UploadThrottle(DebugReports settings) {
        recent = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(settings.uploadIntervalSeconds()))
                .maximumSize(100_000)
                .build();
    }

    /**
     * @return whether this address may upload now; asking counts as uploading
     */
    public boolean admit(String address) {
        return recent.asMap().putIfAbsent(address, Boolean.TRUE) == null;
    }
}
