package de.chojo.lyna.web.api.v1.download.direct;

import de.chojo.lyna.web.api.v1.download.Download;
import de.chojo.lyna.data.access.Products;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.nexus.entities.AssetXO;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ContentType;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Direct {
    private final Download download;
    private final Products products;

    public Direct(Download download, Products products) {
        this.download = download;
        this.products = products;
    }

    public void init() {
        path("direct", () -> {
            get("{product}/{type}/{version}", ctx -> {
                Product product = products.byId(Integer.parseInt(ctx.pathParam("product")))
                        .orElseThrow(() -> new NotFoundResponse("Invalid product id"));
                if (!product.free()) throw new BadRequestResponse("This product is not free.");
                var downloads = product.downloads().byType(Integer.parseInt(ctx.pathParam("type")))
                        .orElseThrow(() -> new NotFoundResponse("Invalid download type"));
                AssetXO asset = downloads.assetByVersion(ctx.pathParam("version"))
                        .orElseThrow(() -> new NotFoundResponse("Unknown version"));
                String filename = "%s-%s.%s".formatted(asset.maven2().artifactId(), asset.maven2().version(), asset.maven2().extension());

                ctx.header("Content-Disposition", "attachment; filename=\"%s\"".formatted(filename))
                        .header("X-Content-Type-Options", "nosniff")
                        .contentType(ContentType.APPLICATION_OCTET_STREAM)
                        .status(HttpStatus.OK)
                        .result(asset.downloadStream().complete());
            });
        });
    }
}
