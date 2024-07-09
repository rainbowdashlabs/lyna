package de.chojo.lyna.web.api.v1.products;

import de.chojo.lyna.web.api.v1.V1;
import de.chojo.lyna.data.dao.products.Product;

import java.util.List;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Products {
    private final V1 v1;
    private final de.chojo.lyna.data.access.Products products;

    public Products(V1 v1, de.chojo.lyna.data.access.Products products) {
        this.v1 = v1;
        this.products = products;
    }

    public void init() {
        path("products", () -> {
            get(ctc -> {
                List<Product> free = products.freeProducts();
                List<SimpleProduct> list = free.stream().map(SimpleProduct::create).toList();
                ctc.json(list);
            });
        });
    }

    private record SimpleProduct(int id, String name, String url) {
        public static SimpleProduct create(Product product) {
            return new SimpleProduct(product.id(), product.name(), product.url());
        }
    }
}
