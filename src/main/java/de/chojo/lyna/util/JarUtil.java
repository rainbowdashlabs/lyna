/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Writes who a download was issued to into the string constants of a jar's classes.
 *
 * <p>A plugin marks the places with placeholders such as {@code %%__USER__%%}. A placeholder is replaced
 * wherever it appears in a constant, not only where it is the whole constant: javac copies a
 * {@code static final} string into every place that uses it and merges it with the text around it, so
 * {@code "licensed to " + USER} is one constant by the time it reaches the jar.
 *
 * <p>Every entry is written anew rather than copied with the metadata it was read with, since a changed
 * class no longer matches the size and checksum an uncompressed entry carries. A class that cannot be
 * read is passed through untouched instead of failing the jar, and the jar's own signature is dropped,
 * because it stops matching the moment a class changes.
 *
 * <p>Originally written by goldmensch.
 */
public final class JarUtil {
    private static final int CLASS_MAGIC = 0xCAFEBABE;
    private static final int UTF8 = 1;
    private static final int LONG = 5;
    private static final int DOUBLE = 6;

    private JarUtil() {}

    /**
     * @return the jar with the replacements applied to every class it holds and to its build data files,
     *     the {@code *.data} files publishdata writes
     */
    public static byte[] replaceStringsInJar(InputStream stream, Map<String, String> replacements) throws IOException {
        var bytesOut = new ByteArrayOutputStream();
        try (var outputZip = new ZipOutputStream(bytesOut);
                var inputZip = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = inputZip.getNextEntry()) != null) {
                if (isSignature(entry.getName())) continue;
                outputZip.putNextEntry(copyOf(entry));
                if (!entry.isDirectory()) {
                    byte[] content = inputZip.readAllBytes();
                    outputZip.write(replaceIn(entry.getName(), content, replacements));
                }
                outputZip.closeEntry();
            }
        }
        return bytesOut.toByteArray();
    }

    /**
     * @return the class with the replacements applied to its string constants
     * @throws IOException if the stream is not a class file this can read
     */
    public static byte[] replaceStringsInClass(InputStream inputStream, Map<String, String> replacements)
            throws IOException {
        DataInputStream input = new DataInputStream(inputStream);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(byteArrayOutputStream);

        int magic = input.readInt();
        if (magic != CLASS_MAGIC) throw new IOException("Not a class file");
        output.writeInt(magic);
        output.write(input.readNBytes(4));
        int poolCount = input.readUnsignedShort();
        output.writeShort(poolCount);

        for (int i = 1; i < poolCount; i++) {
            int tag = input.readUnsignedByte();
            output.write(tag);
            if (tag == UTF8) {
                output.writeUTF(replace(input.readUTF(), replacements));
                continue;
            }
            if (tag == LONG || tag == DOUBLE) i++;
            output.write(input.readNBytes(constantLength(tag)));
        }

        output.write(input.readAllBytes());
        return byteArrayOutputStream.toByteArray();
    }

    private static byte[] replaceIn(String name, byte[] content, Map<String, String> replacements) {
        if (name.endsWith(".class")) return replaceOrKeep(content, replacements);
        if (name.endsWith(".data")) {
            return replace(new String(content, StandardCharsets.UTF_8), replacements)
                    .getBytes(StandardCharsets.UTF_8);
        }
        return content;
    }

    private static byte[] replaceOrKeep(byte[] content, Map<String, String> replacements) {
        try {
            return replaceStringsInClass(new ByteArrayInputStream(content), replacements);
        } catch (IOException | IllegalArgumentException e) {
            return content;
        }
    }

    private static String replace(String constant, Map<String, String> replacements) {
        String replaced = constant;
        for (var replacement : replacements.entrySet()) {
            replaced = replaced.replace(replacement.getKey(), replacement.getValue());
        }
        return replaced;
    }

    /**
     * The length of a constant pool entry after its tag, for every kind but UTF-8, whose length is part of
     * the entry.
     */
    private static int constantLength(int tag) {
        return switch (tag) {
            case 3, 4, 9, 10, 11, 12, 17, 18 -> 4;
            case 7, 8, 16, 19, 20 -> 2;
            case LONG, DOUBLE -> 8;
            case 15 -> 3;
            default -> throw new IllegalArgumentException("Unknown constant pool tag %d".formatted(tag));
        };
    }

    private static ZipEntry copyOf(ZipEntry entry) {
        ZipEntry copy = new ZipEntry(entry.getName());
        copy.setTime(entry.getTime());
        if (entry.getComment() != null) copy.setComment(entry.getComment());
        return copy;
    }

    /**
     * Whether an entry is part of the jar's signature: the signature files directly under
     * {@code META-INF/}.
     */
    private static boolean isSignature(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("META-INF/") || upper.indexOf('/', "META-INF/".length()) >= 0) return false;
        return upper.endsWith(".SF")
                || upper.endsWith(".RSA")
                || upper.endsWith(".DSA")
                || upper.endsWith(".EC")
                || upper.startsWith("META-INF/SIG-");
    }
}
