package de.chojo.lyna.util;

import de.chojo.lyna.data.dao.products.Product;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LicenseCreatorTest {

    @Test
    void create() {
        Product product = mock(Product.class);
        when(product.id()).thenReturn(1);
        String license = LicenseCreator.create(1234, product, "854264");
        System.out.println(license);
    }
}
