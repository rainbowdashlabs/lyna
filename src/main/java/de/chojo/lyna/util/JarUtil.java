package de.chojo.lyna.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarUtil {
    public static JarInputStream openJar(final InputStream file) throws IOException {
        final ZipInputStream stream = new ZipInputStream(file);

        ZipEntry zipEntry;
        while ((zipEntry = stream.getNextEntry()) != null) {
            final String name = zipEntry.getName();
            if (!zipEntry.isDirectory() && name.split("/").length == 1 && name.endsWith(".jar")) {
                // todo what about multiple jars in one zip?
                return new JarInputStream(stream);
            }
        }

        throw new RuntimeException("version.new.error.jarNotFound");
    }

    // Made by goldmensch
    public static byte[] replaceStringInJar(byte[] bytes, Map<String, String> replacements)  {
        int current = 10;
        int poolCount = (Byte.toUnsignedInt(bytes[8]) << 8) | Byte.toUnsignedInt(bytes[9]) - 1;

        Map<String, byte[]> byteMap = replacements.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getBytes(StandardCharsets.UTF_8)));

        for (int i = 0; i < poolCount; i++) {
            int tag = bytes[current];
            if (tag == 1) {
                current++;
                var start = current;
                int length = (Byte.toUnsignedInt(bytes[current++]) << 8) | Byte.toUnsignedInt(bytes[current++]);
                byte[] stringByes = Arrays.copyOfRange(bytes, current, current + length);
                String str = new String(stringByes);
                current += stringByes.length;

                for (var entry : byteMap.entrySet()) {
                    if (!str.equals(entry.getKey())) {
                        continue;
                    }
                    var first = Arrays.copyOfRange(bytes, 0, start);
                    var second = Arrays.copyOfRange(bytes, current, bytes.length);
                    List<Byte> byteList = new ArrayList<>();

                    for (byte b : first) {
                        byteList.add(b);
                    }

                    byteList.add((byte) ((entry.getValue().length & 0xFF00) >> 8));
                    byteList.add((byte) entry.getValue().length);

                    for (byte userNameByte : entry.getValue()) {
                        byteList.add(userNameByte);
                    }

                    for (byte b : second) {
                        byteList.add(b);
                    }

                    var finalBytes = new byte[byteList.size()];
                    for (int i1 = 0; i1 < byteList.size(); i1++) {
                        finalBytes[i1] = byteList.get(i1);
                    }

                    bytes = finalBytes;
                    current = start + 2 + entry.getValue().length;
                }
            } else {
                var skip = switch (tag) {
                    case 10, 9, 11, 12, 18, 3, 4 -> 5;
                    case 8, 16, 7 -> 3;
                    case 5, 6 -> {
                        i++;
                        yield 9;
                    }
                    case 15 -> 4;
                    default -> throw new IllegalArgumentException("No tag found for %s".formatted(tag));
                };
                current += skip;
            }
        }
        return bytes;

    }

    public record Jar(String fileName, JarInputStream stream) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            this.stream.close();
        }
    }
}
