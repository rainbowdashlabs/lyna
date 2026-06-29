package de.chojo.lyna.web.api.v1.download.proxy;

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
        Integer licenseId
) {
    public AssetDownload(String assetId, Runnable postDownload, String userId) {
        this(assetId, postDownload, userId, null, null, null, null, null, null, null);
    }

    public AssetDownload withDownloadContext(int productId, int downloadId, String version, String source,
                                              Integer accountId, Long discordId, Integer licenseId) {
        return new AssetDownload(assetId, postDownload, userId, productId, downloadId, version, source,
                accountId, discordId, licenseId);
    }
}
