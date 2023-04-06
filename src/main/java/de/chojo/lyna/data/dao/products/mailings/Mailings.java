package de.chojo.lyna.data.dao.products.mailings;

import de.chojo.lyna.data.dao.platforms.Platform;
import de.chojo.lyna.data.dao.products.Product;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Mailings {
    private final Product product;

    public Mailings(Product product) {
        this.product = product;
    }

    public Mailing create(Platform platform, String name, String mailText) {
        return builder(Mailing.class).query("""
                        INSERT INTO mail_products(product_id, platform_id, name, mail_text) VALUES (?,?,?,?) RETURNING id
                        """)
                .parameter(stmt -> stmt.setInt(product.id()).setInt(platform.id()).setString(name).setString(mailText))
                .readRow(row -> new Mailing(row.getInt("id"), product, platform, name, mailText))
                .firstSync()
                .get();
    }
}
