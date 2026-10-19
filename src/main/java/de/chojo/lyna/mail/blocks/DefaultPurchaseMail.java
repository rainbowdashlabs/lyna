/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.mail.blocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The purchase mail the application ships, which every product sends until somebody writes another.
 *
 * <p>Kept as a block document in {@code mail/default-purchase.json}, so it is composed from exactly
 * what an operator can compose and loads into the editor as it is.
 */
public final class DefaultPurchaseMail {
    private static final String RESOURCE = "/mail/default-purchase.json";

    /** The shipped default, as a block document. */
    public static final String BLOCKS = read();

    private DefaultPurchaseMail() {}

    private static String read() {
        try (InputStream in = DefaultPurchaseMail.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " is missing from the build");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
