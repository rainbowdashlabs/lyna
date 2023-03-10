package de.chojo.lyna.api.v1.update;

import de.chojo.lyna.api.v1.V1;
import de.chojo.lyna.data.access.Products;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.util.Version;
import io.javalin.http.HttpCode;

import java.util.Optional;
import java.util.Set;

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
                    ctx.status(HttpCode.BAD_REQUEST)
                            .result("Invalid id");
                    return;
                }

                Optional<Product> optProduct = products.byId(id);

                if (optProduct.isEmpty()) {
                    ctx.status(HttpCode.NOT_FOUND)
                            .result("Unknown id");
                    return;
                }
                Product product = optProduct.get();
                String versionString = ctx.queryParam("version");
                if (versionString == null) {
                    ctx.status(HttpCode.BAD_REQUEST)
                            .result("Missing version");
                    return;
                }
                Version current = Version.parse(versionString);

                Set<ReleaseType> descendants = current.type().descendants();

                Optional<Version> latest = product.latestVersion(descendants);
                if (latest.isEmpty()) {
                    ctx.status(HttpCode.OK).json(new UpdateResponse(false, current.version()));
                    return;
                }
                ctx.status(HttpCode.OK).json(new UpdateResponse(latest.get().isNewer(current), latest.get().version()));
            });
        });
    }

    public record UpdateResponse(boolean update, String latest) {
    }
}
