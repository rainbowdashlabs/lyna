package de.chojo.lyna.api.v1.download.proxy;

public record AssetDownload(String assetId, Runnable postDownload, String userId) {
}
