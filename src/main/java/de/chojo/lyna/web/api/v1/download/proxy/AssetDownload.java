package de.chojo.lyna.web.api.v1.download.proxy;

public record AssetDownload(String assetId, Runnable postDownload, String userId) {
}
