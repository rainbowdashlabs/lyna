/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.icon.repository.ProductIconRepository;
import de.chojo.lyna.feature.icon.service.ProductIconService;
import de.chojo.lyna.feature.icon.storage.IconStorage;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Turning an upload into the sizes a page draws.
 *
 * <p>What is refused matters as much as what is kept: the type is read from the bytes rather than
 * from what the upload claimed, so a file that is not an image cannot become one by being named like
 * one.
 */
class ProductIconServiceTest extends RepositoryTestBase {
    private static final long GUILD = 4801L;

    private ProductIconService icons;
    private IconStorage storage;
    private int productId;

    @BeforeEach
    void seed(@TempDir Path directory) throws SQLException {
        clear("product_icon", "product");
        storage = new IconStorage(directory);
        icons = new ProductIconService(storage, new ProductIconRepository());
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "INSERT INTO %s.product (guild_id, name, role) VALUES (%d, 'Widget', 5) RETURNING id"
                                .formatted(schemaName, GUILD))) {
            rows.next();
            productId = rows.getInt(1);
        }
    }

    /** A picture of a known size, in a format lyna accepts. */
    private static byte[] image(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.MAGENTA);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("A PNG and a JPEG are recognised by their first bytes")
    void theTypeComesFromTheBytes() throws Exception {
        assertEquals("image/png", ProductIconService.sniff(image(8, 8, "png")).orElseThrow());
        assertEquals("image/jpeg", ProductIconService.sniff(image(8, 8, "jpg")).orElseThrow());
        // A WebP header, which ImageIO here can read but not write, so it is spelled out rather than
        // produced.
        byte[] webp = new byte[] {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};
        assertEquals("image/webp", ProductIconService.sniff(webp).orElseThrow());
    }

    @Test
    @DisplayName("Anything else is not an image, whatever it was called")
    void everythingElseIsRefused() {
        assertTrue(ProductIconService.sniff("not an image at all".getBytes()).isEmpty());
        assertTrue(
                ProductIconService.sniff(new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0, 0, 0, 0, 0})
                        .isEmpty(),
                "a GIF would lose its animation to the resizer, so it is not accepted");
        assertTrue(ProductIconService.sniff(new byte[0]).isEmpty());
        assertTrue(ProductIconService.sniff(null).isEmpty());
    }

    @Test
    @DisplayName("An upload is kept at every size a page asks for")
    void everySizeIsStored() throws Exception {
        assertEquals("image/png", icons.store(productId, image(400, 400, "png")).orElseThrow());

        for (int size : ProductIconService.SIZES) {
            assertTrue(storage.read(productId, size).isPresent(), "missing " + size);
        }
        assertTrue(storage.read(productId, ProductIconService.MAX_PIXEL_SIZE).isPresent());
    }

    @Test
    @DisplayName("A large picture is cut down rather than stored as it arrived")
    void theOriginalIsCapped() throws Exception {
        icons.store(productId, image(1600, 1600, "png"));

        BufferedImage largest = ImageIO.read(new java.io.ByteArrayInputStream(
                storage.read(productId, ProductIconService.MAX_PIXEL_SIZE).orElseThrow()));
        assertEquals(ProductIconService.MAX_PIXEL_SIZE, largest.getWidth());
    }

    @Test
    @DisplayName("A small picture is not blown up to fill a size it never had")
    void nothingIsScaledUp() throws Exception {
        icons.store(productId, image(48, 48, "png"));

        BufferedImage stored = ImageIO.read(
                new java.io.ByteArrayInputStream(storage.read(productId, 256).orElseThrow()));
        assertEquals(48, stored.getWidth());
    }

    @Test
    @DisplayName("A smaller size is drawn smaller, which is the point of keeping several")
    void thesizesDiffer() throws Exception {
        icons.store(productId, image(400, 400, "png"));

        BufferedImage big = ImageIO.read(
                new java.io.ByteArrayInputStream(storage.read(productId, 256).orElseThrow()));
        BufferedImage small = ImageIO.read(
                new java.io.ByteArrayInputStream(storage.read(productId, 64).orElseThrow()));
        assertEquals(256, big.getWidth());
        assertEquals(64, small.getWidth());
    }

    @Test
    @DisplayName("Asking for a size between two is answered with the larger, never a blurred one")
    void readingRoundsUp() throws Exception {
        icons.store(productId, image(400, 400, "png"));

        BufferedImage served = ImageIO.read(
                new java.io.ByteArrayInputStream(icons.read(productId, 100).orElseThrow()));
        assertEquals(128, served.getWidth());
    }

    @Test
    @DisplayName("A file that is not an image is refused rather than stored")
    void notAnImageIsRefused() throws Exception {
        assertTrue(icons.store(productId, "still not an image".getBytes()).isEmpty());
        assertTrue(storage.read(productId, 128).isEmpty());
    }

    @Test
    @DisplayName("Something far too large to be an icon is refused before it is read")
    void tooLargeIsRefused() {
        byte[] huge = new byte[5 * 1024 * 1024];
        assertThrows(IllegalArgumentException.class, () -> icons.store(productId, huge));
    }

    @Test
    @DisplayName("Removing an icon takes every size with it")
    void removingTakesEverything() throws Exception {
        icons.store(productId, image(200, 200, "png"));

        icons.remove(productId);

        for (int size : ProductIconService.SIZES) {
            assertTrue(storage.read(productId, size).isEmpty(), "still there: " + size);
        }
    }

    @Test
    @DisplayName("A key cannot be talked out of the icon directory")
    void theStorageStaysInItsDirectory(@TempDir Path directory) {
        IconStorage guarded = new IconStorage(directory.resolve("icons"));

        assertTrue(guarded.read(1, 64).isEmpty(), "nothing there yet, and no escape attempted");
        guarded.write(1, 64, new byte[] {1, 2, 3});
        assertTrue(guarded.read(1, 64).isPresent());
    }
}
