package de.chojo.lyna.api.v1.download.proxy;

import de.chojo.lyna.data.dao.products.downloads.Download;

public record AssetDownload(Download download, String assetId) {
}
