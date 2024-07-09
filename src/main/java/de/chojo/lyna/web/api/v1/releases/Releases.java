package de.chojo.lyna.web.api.v1.releases;

import de.chojo.lyna.web.api.v1.V1;
import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.dao.products.downloads.Download;
import de.chojo.nexus.entities.AssetXO;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;

import java.time.Instant;
import java.util.List;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Releases {
    private final V1 v1;
    private final de.chojo.lyna.data.access.Products products;

    public Releases(V1 v1, de.chojo.lyna.data.access.Products products) {
        this.v1 = v1;
        this.products = products;
    }

    public void init() {
        path("releases", () -> {
            get("{product}", ctx -> {
                Product product = products.byId(Integer.parseInt(ctx.pathParam("product")))
                        .orElseThrow(() -> new NotFoundResponse("Invalid product id"));

                if (!product.free()) throw new BadRequestResponse("This product is not free");

                List<SimpleType> downloads = product.downloads()
                        .downloads()
                        .stream()
                        .sorted()
                        .map(SimpleType::create)
                        .toList();
                ctx.json(downloads);
            });

            get("{product}/{type}", ctx -> {
                Product product = products.byId(Integer.parseInt(ctx.pathParam("product")))
                        .orElseThrow(() -> new NotFoundResponse("Invalid product id"));

                if (!product.free()) throw new BadRequestResponse("This product is not free");

                Download download = product.downloads().byType(Integer.parseInt(ctx.pathParam("type")))
                        .orElseThrow(() -> new NotFoundResponse("Invalid type id"));
                var assets = download.latestAssets()
                        .stream()
                        .map(e -> SimpleAsset.create(download.type().id(), e))
                        .toList();
                ctx.json(assets);
            });
        });
    }

    private record SimpleType(int id, String name, String description) {
        public static SimpleType create(Download download) {
            DownloadType type = download.type();
            return new SimpleType(type.id(), type.name(), type.description());
        }
    }

    private record SimpleAsset(String version, int type, Instant published) {
        public static SimpleAsset create(int type, AssetXO asset){
            return new SimpleAsset(asset.maven2().version(), type, asset.lastModified().toInstant());
        }
    }
}
