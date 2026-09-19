/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.icon.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.icon.repository.ProductIconRepository;
import de.chojo.lyna.feature.icon.storage.IconStorage;
import net.coobird.thumbnailator.Thumbnails;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

/**
 * Turning what somebody uploaded into the sizes a page draws.
 *
 * <p>The type is read from the first bytes rather than from what the upload claimed: a browser will
 * send whatever it likes, and what matters is what the file actually is. Anything that is not a still
 * image lyna can resize is refused, which is why an animated GIF is refused as well - the resizer
 * keeps its first frame, and an icon that silently stops moving is worse than one that was not
 * accepted.
 */
@Singleton
public class ProductIconService {
    /** What an icon is cut down to before anything else, so nobody stores a photograph. */
    public static final int MAX_PIXEL_SIZE = 512;

    /**
     * The sizes kept. An icon is drawn at about 40 pixels on a tile and about 96 on a page, and each
     * of those doubles on a dense screen: past 256 nothing would ever be shown.
     */
    public static final List<Integer> SIZES = List.of(256, 128, 64);

    private static final double COMPRESSION_QUALITY = 0.85;
    private static final int MAX_UPLOAD_BYTES = 4 * 1024 * 1024;

    private final IconStorage storage;
    private final ProductIconRepository icons;

    @Inject
    public ProductIconService(IconStorage storage, ProductIconRepository icons) {
        this.storage = storage;
        this.icons = icons;
    }

    /**
     * Reads the type out of the first bytes of a file.
     *
     * <p>PNG, JPEG and WebP each begin with a signature nothing else does.
     *
     * @return the type, or nothing when it is not one of those
     */
    public static Optional<String> sniff(byte[] data) {
        if (data == null || data.length < 12) return Optional.empty();
        if (startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return Optional.of("image/png");
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) return Optional.of("image/jpeg");
        if (startsWith(data, 0x52, 0x49, 0x46, 0x46)
                && data[8] == 0x57
                && data[9] == 0x45
                && data[10] == 0x42
                && data[11] == 0x50) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    /**
     * Stores an upload as the icon of a product.
     *
     * @param data what was uploaded
     * @return the type it was stored as, or nothing when the upload is not an image lyna accepts
     * @throws IllegalArgumentException if the upload is larger than an icon has any need to be
     */
    public Optional<String> store(int productId, byte[] data) throws IOException {
        if (data.length > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("An icon is at most %d MB".formatted(MAX_UPLOAD_BYTES / 1024 / 1024));
        }
        Optional<String> mime = sniff(data);
        if (mime.isEmpty()) return Optional.empty();

        BufferedImage source = ImageIO.read(new ByteArrayInputStream(data));
        if (source == null) return Optional.empty();

        String extension = extensionFor(mime.get());
        int longest = Math.max(source.getWidth(), source.getHeight());
        // The largest kept size is the smaller of the cap and what arrived: nothing is scaled up.
        storage.write(productId, MAX_PIXEL_SIZE, resize(source, Math.min(longest, MAX_PIXEL_SIZE), extension));
        for (int size : SIZES) {
            storage.write(productId, size, resize(source, Math.min(longest, size), extension));
        }
        icons.record(productId, mime.get());
        return mime;
    }

    /**
     * Reads the nearest stored size that is at least as large as the one asked for, so a page asking
     * for something between two sizes is never served a blurred one.
     */
    public Optional<byte[]> read(int productId, int requested) {
        int chosen = SIZES.stream()
                .sorted()
                .filter(size -> size >= requested)
                .findFirst()
                .orElse(MAX_PIXEL_SIZE);
        return storage.read(productId, chosen);
    }

    public void remove(int productId) {
        storage.deleteAll(productId);
        icons.remove(productId);
    }

    private static byte[] resize(BufferedImage source, int longestSide, String extension) throws IOException {
        double scale = (double) longestSide / Math.max(source.getWidth(), source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(source)
                .size(width, height)
                .outputQuality(COMPRESSION_QUALITY)
                .outputFormat(extension)
                .toOutputStream(out);
        return out.toByteArray();
    }

    private static String extensionFor(String mime) {
        return switch (mime.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }
}
