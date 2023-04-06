package de.chojo.lyna.data.dao.products.mailings;

import de.chojo.lyna.data.dao.platforms.Platform;
import de.chojo.lyna.data.dao.products.Product;

public class Mailing {
    private final int id;
    private final Product product;
    private final Platform platform;
    private final String name;
    private final String mailText;

    public Mailing(int id, Product product, Platform platform, String name, String mailText) {
        this.id = id;
        this.product = product;
        this.platform = platform;
        this.name = name;
        this.mailText = mailText;
    }

    public Product product() {
        return product;
    }

    public Platform platform() {
        return platform;
    }

    public String name() {
        return name;
    }

    public String mailText() {
        return mailText;
    }
}
