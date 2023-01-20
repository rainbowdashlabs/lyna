package de.chojo.lyna.util;

import de.chojo.lyna.data.dao.LicenseUser;
import de.chojo.lyna.data.dao.platforms.Platform;
import de.chojo.lyna.data.dao.products.Product;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LicenseCreatorTest {

    @Test
    void create() {
        Platform platform = mock(Platform.class);
        when(platform.id()).thenReturn(1);
        Product product = mock(Product.class);
        when(product.id()).thenReturn(1);
        String license = LicenseCreator.create(1234, product, platform, "854264");
        System.out.println(license);
    }
}
