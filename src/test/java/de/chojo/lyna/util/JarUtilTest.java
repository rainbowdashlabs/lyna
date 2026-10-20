/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarUtilTest {
    private static final Map<String, String> REPLACEMENTS =
            Map.of("%%__USER__%%", "testId", "%%__RESOURCE__%%", "testResource", "%%__NONCE__%%", "testNonce");

    private static byte[] userData() throws IOException {
        try (InputStream in = JarUtilTest.class.getClassLoader().getResourceAsStream("UserData.class")) {
            return in.readAllBytes();
        }
    }

    private static List<String> strings(byte[] classFile) {
        return StreamSupport.stream(
                        ClassFile.of().parse(classFile).constantPool().spliterator(), false)
                .filter(Utf8Entry.class::isInstance)
                .map(entry -> ((Utf8Entry) entry).stringValue())
                .toList();
    }

    private static Map<String, byte[]> entries(byte[] jar) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
        }
        return entries;
    }

    private static void stored(ZipOutputStream zip, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        CRC32 crc = new CRC32();
        crc.update(content);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    @Test
    @DisplayName("Every placeholder is replaced, also inside a longer constant, and the class still parses")
    void replacesInClass() throws IOException {
        byte[] replaced = JarUtil.replaceStringsInClass(new ByteArrayInputStream(userData()), REPLACEMENTS);

        List<String> strings = strings(replaced);
        assertTrue(strings.containsAll(List.of("testId", "testResource", "testNonce", "Nonce: testNonce")));
        assertTrue(strings.stream().noneMatch(string -> string.contains("%%__")));
    }

    @Test
    @DisplayName("Something that is not a class file is refused")
    void refusesWhatIsNoClass() {
        assertThrows(
                IOException.class,
                () -> JarUtil.replaceStringsInClass(
                        new ByteArrayInputStream("not a class".getBytes(StandardCharsets.UTF_8)), REPLACEMENTS));
    }

    @Test
    @DisplayName("A real jar comes out with no placeholder left in any class")
    void replacesInJar() throws IOException {
        byte[] jar;
        try (InputStream in = JarUtilTest.class.getClassLoader().getResourceAsStream("TestJar.jar")) {
            jar = JarUtil.replaceStringsInJar(in, REPLACEMENTS);
        }

        Map<String, byte[]> entries = entries(jar);
        assertFalse(entries.isEmpty());
        entries.forEach((name, content) -> {
            if (name.endsWith(".class")) {
                assertTrue(strings(content).stream().noneMatch(string -> string.contains("%%__")), name);
            }
        });
    }

    @Test
    @DisplayName("Uncompressed entries, directories and resources survive; a broken class passes untouched")
    void keepsTheJarIntact() throws IOException {
        byte[] broken = "garbage".getBytes(StandardCharsets.UTF_8);
        byte[] resource = "user: %%__USER__%%".getBytes(StandardCharsets.UTF_8);
        var input = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(input)) {
            zip.putNextEntry(new ZipEntry("pkg/"));
            zip.closeEntry();
            stored(zip, "pkg/UserData.class", userData());
            stored(zip, "pkg/Broken.class", broken);
            stored(zip, "plugin.yml", resource);
        }

        Map<String, byte[]> entries =
                entries(JarUtil.replaceStringsInJar(new ByteArrayInputStream(input.toByteArray()), REPLACEMENTS));

        assertEquals(
                List.of("pkg/", "pkg/UserData.class", "pkg/Broken.class", "plugin.yml"), List.copyOf(entries.keySet()));
        assertTrue(strings(entries.get("pkg/UserData.class")).contains("testId"));
        assertArrayEquals(broken, entries.get("pkg/Broken.class"));
        assertArrayEquals(resource, entries.get("plugin.yml"), "only classes are rewritten");
    }

    @Test
    @DisplayName("The jar's own signature is dropped, since a changed class no longer matches it")
    void dropsSignature() throws IOException {
        var input = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(input)) {
            for (String name : List.of(
                    "META-INF/MANIFEST.MF",
                    "META-INF/CERT.SF",
                    "META-INF/CERT.RSA",
                    "META-INF/KEY.DSA",
                    "META-INF/KEY.EC",
                    "META-INF/SIG-ONE",
                    "META-INF/services/x.RSA")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(1);
                zip.closeEntry();
            }
        }

        Map<String, byte[]> entries =
                entries(JarUtil.replaceStringsInJar(new ByteArrayInputStream(input.toByteArray()), REPLACEMENTS));

        assertEquals(List.of("META-INF/MANIFEST.MF", "META-INF/services/x.RSA"), List.copyOf(entries.keySet()));
    }
}
