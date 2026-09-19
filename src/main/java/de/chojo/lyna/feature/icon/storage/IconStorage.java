/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.icon.storage;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.configuration.elements.Storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The files an icon is made of, on disk.
 *
 * <p>A directory per product holding one file per size. Written to a temporary name and moved into
 * place, so a reader never sees a half-written file and a failed write leaves the previous icon where
 * it was.
 *
 * <p>Every path is resolved against the root and checked to still be under it. Nothing here takes a
 * key from outside, but a store that can be talked out of its own directory is one bad call away from
 * writing anywhere, and the check costs nothing.
 */
@Singleton
public class IconStorage {
    private final Path root;

    @Inject
    public IconStorage(Storage config) {
        this(Path.of(config.directory()).resolve("icons"));
    }

    public IconStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * Writes one size of one product's icon.
     */
    public void write(int productId, int size, byte[] bytes) {
        Path target = resolve(productId, size);
        Path partial = target.resolveSibling(target.getFileName() + ".partial." + UUID.randomUUID());
        try {
            Files.createDirectories(target.getParent());
            Files.write(partial, bytes);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteQuietly(partial);
            throw new UncheckedIOException("Could not write the icon for product " + productId, e);
        }
    }

    /**
     * @return the bytes of that size, or nothing when the product has no icon at it
     */
    public Optional<byte[]> read(int productId, int size) {
        Path path = resolve(productId, size);
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the icon for product " + productId, e);
        }
    }

    /**
     * Removes every size a product has, and the directory holding them.
     */
    public void deleteAll(int productId) {
        Path directory = directory(productId);
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(IconStorage::deleteQuietly);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not remove the icon for product " + productId, e);
        }
    }

    private Path directory(int productId) {
        return under(Integer.toString(productId));
    }

    private Path resolve(int productId, int size) {
        return under(productId + "/" + size);
    }

    /**
     * @throws IllegalArgumentException if the key would land outside the root
     */
    private Path under(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Key escapes the icon directory: " + key);
        }
        return resolved;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A leftover temporary file is not worth failing a request over.
        }
    }
}
