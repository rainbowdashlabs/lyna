package de.chojo.lyna.web.api.v1.update;

import de.chojo.lyna.data.access.Products;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.dao.products.downloads.Download;
import de.chojo.lyna.util.Version;
import de.chojo.lyna.web.api.v1.V1;
import de.chojo.nexus.entities.AssetXO;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Update {
    private final V1 v1;
    private final Products products;

    public Update(V1 v1, Products products) {
        this.v1 = v1;
        this.products = products;
    }

    public void init() {
        path("update", () -> {
            // ?id=<>&version=<>
            get("check", ctx -> {
                int id;
                try {
                    id = Integer.parseInt(ctx.queryParam("id"));
                } catch (NumberFormatException e) {
                    ctx.status(HttpStatus.BAD_REQUEST)
                            .result("Invalid id");
                    return;
                }

                Optional<Product> optProduct = products.byId(id);

                if (optProduct.isEmpty()) {
                    ctx.status(HttpStatus.NOT_FOUND)
                            .result("Unknown id");
                    return;
                }
                Product product = optProduct.get();
                String versionString = ctx.queryParam("version");
                if (versionString == null) {
                    ctx.status(HttpStatus.BAD_REQUEST)
                            .result("Missing version");
                    return;
                }

                @Nullable String artifact = ctx.queryParam("artifact");

                @Nullable String unixString = ctx.queryParam("unix");

                long unix;
                try {
                    unix = unixString == null ? 0 : Long.parseLong(unixString);
                } catch (NumberFormatException e) {
                    ctx.status(HttpStatus.BAD_REQUEST)
                            .result("Invalid unix timestamp");
                    return;
                }

                @Nullable Instant created = unix == 0 ? null : Instant.ofEpochSecond(unix);

                Version current = Version.parse(versionString);
                UpdateResponse response;
                try {
                    response = switch (current.type()) {
                        case STABLE -> handleStableBuild(product, current, artifact);
                        case DEV -> handleDevBuild(product, created, current, artifact);
                        case SNAPSHOT -> handleSnapshotBuild(product, created, current, artifact);
                    };
                } catch (RuntimeException e) {
                    response = new UpdateResponse(false, versionString, unix);
                }

                ctx.status(HttpStatus.OK).json(response);
            });
        });
    }

    private UpdateResponse handleStableBuild(Product product, Version current, @Nullable String artifact) {
        // Find stable release with artifact
        Optional<Download> download = getDownload(product, ReleaseType.STABLE, artifact);

        if (download.isEmpty()) {
            return new UpdateResponse(false, current.version(), 0);
        }

        // Get the latest asset for this release
        List<AssetXO> assetXOS = download.get().latestAssets();
        if (assetXOS.isEmpty()) {
            return new UpdateResponse(false, current.version(), 0);
        }

        AssetXO latestAsset = assetXOS.get(0);

        Version latest = Version.parse(latestAsset.maven2().version());

        // simply check if the latest version is newer than the current
        return new UpdateResponse(latest.isNewer(current), latest.version(), latestAsset.lastModified().toEpochSecond());
    }

    private UpdateResponse handleDevBuild(Product product, @Nullable Instant created, Version current, @Nullable String artifact) {
        UpdateResponse stableResponse = handleStableBuild(product, current, artifact);
        if (stableResponse.update()) {
            return stableResponse;
        }

        // Find dev release with artifact
        return getDownload(product, ReleaseType.DEV, artifact)
                .or(() -> product.downloads().byReleaseType(ReleaseType.DEV))
                .map(value -> evaluateDevAndSnapshotVersion(value, current, created))
                .orElseGet(() -> new UpdateResponse(false, current.version(), 0));

    }

    private UpdateResponse handleSnapshotBuild(Product product, @Nullable Instant created, Version current, @Nullable String artifact) {
        UpdateResponse stableResponse = handleStableBuild(product, current, artifact);
        if (stableResponse.update()) {
            return stableResponse;
        }

        // The dev build might be newer than the snapshot but isn't necessarily. We keep it for now.
        UpdateResponse devResponse = handleDevBuild(product, created, current, artifact);

        Optional<Download> download = getDownload(product, ReleaseType.SNAPSHOT, artifact);
        if (download.isEmpty()) {
            return new UpdateResponse(false, current.version(), 0);
        }

        UpdateResponse snapshotResponse = evaluateDevAndSnapshotVersion(download.get(), current, created);
        if (snapshotResponse.update() && devResponse.update()) {
            // determine whether dev or snapshot is newer.
            return snapshotResponse.published() > devResponse.published() ? snapshotResponse : devResponse;
        }

        return snapshotResponse;
    }

    private UpdateResponse evaluateDevAndSnapshotVersion(Download download, Version current, @Nullable Instant created) {
        List<AssetXO> assetXOS = download.latestAssets();

        if (assetXOS.isEmpty()) {
            return new UpdateResponse(false, current.version(), 0);
        }
        AssetXO latestAsset = assetXOS.get(0);

        Version latest = Version.parse(latestAsset.maven2().version());

        // Check if there is a newer build than the current
        if (latest.isNewer(current)) {
            return new UpdateResponse(true, latest.version(), assetAge(latestAsset));
        }

        if (latest.isOlder(current)) {
            // Should not happen unless we do stupid stuff like deleting released versions
            return new UpdateResponse(false, latest.version(), 0);
        }

        // Equal versions from here on.
        // Since dev and snapshot builds are overridden on publish we need to check if there is a newer build time.

        if (created == null) {
            return new UpdateResponse(false, latest.version(), 0);
        }

        long seconds = created.until(Instant.now(), ChronoUnit.SECONDS);
        // Check if the current build is newer than 5 minutes
        if (created.isBefore(latestAsset.lastModified().toInstant())) {
            return new UpdateResponse(true, latest.version(), assetAge(latestAsset));
        }
        return new UpdateResponse(false, latest.version(), 0);
    }

    private Optional<Download> getDownload(Product product, ReleaseType type, @Nullable String artifact) {
        return product.downloads().byReleaseTypeAndArtifact(type, artifact)
                .or(() -> product.downloads().byReleaseType(type));
    }

    private long assetAge(AssetXO assetXO) {
        return assetXO.lastModified().toEpochSecond();
    }

    public record UpdateResponse(boolean update, String latest, long published) {

    }
}
