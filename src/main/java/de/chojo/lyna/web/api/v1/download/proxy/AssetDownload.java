/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.download.proxy;

/**
 * A download somebody has been given a one-time link for.
 *
 * @param filename what the browser saves it as; null falls back to the artifact's own name
 */
public record AssetDownload(
        String assetId,
        Runnable postDownload,
        String userId,
        Integer productId,
        Integer downloadId,
        String version,
        String source,
        Integer accountId,
        Long discordId,
        Integer licenseId,
        String filename) {
    public AssetDownload(String assetId, Runnable postDownload, String userId) {
        this(assetId, postDownload, userId, null, null, null, null, null, null, null, null);
    }

    public AssetDownload withFilename(String filename) {
        return new AssetDownload(
                assetId,
                postDownload,
                userId,
                productId,
                downloadId,
                version,
                source,
                accountId,
                discordId,
                licenseId,
                filename);
    }

    public AssetDownload withDownloadContext(
            int productId,
            int downloadId,
            String version,
            String source,
            Integer accountId,
            Long discordId,
            Integer licenseId) {
        return new AssetDownload(
                assetId,
                postDownload,
                userId,
                productId,
                downloadId,
                version,
                source,
                accountId,
                discordId,
                licenseId,
                filename);
    }
}
