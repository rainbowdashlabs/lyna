package de.chojo.lyna.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarUtil {

    // Made by goldmensch (contact goldmensch on discord, or write email to nickhensel25@icloud.com if it breaks)
    public static byte[] replaceStringInJar(InputStream inputStream, Map<String, String> replacements) throws IOException {
        DataInputStream input = new DataInputStream(inputStream);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(byteArrayOutputStream);

        output.write(input.readNBytes(8));
        int poolCount = input.readUnsignedShort() - 1;
        output.writeShort((poolCount + 1) & 0xFFFF);

        for (int i = 0; i < poolCount; i++) {
            int tag = input.readUnsignedByte();
            output.write(tag);
            if (tag == 1) {
                String str = input.readUTF();

                var out = replacements.entrySet().stream()
                        .filter(entry -> entry.getKey().equals(str))
                        .findAny()
                        .map(Map.Entry::getValue)
                        .orElse(str);
                output.writeUTF(out);
            } else {
                var skip = switch (tag) {
                    case 10, 9, 11, 12, 18, 3, 4 -> 4;
                    case 8, 16, 7 -> 2;
                    case 5, 6 -> {
                        i++;
                        yield 8;
                    }
                    case 15 -> 3;
                    default -> throw new IllegalArgumentException("No tag found for %s".formatted(tag));
                };
                output.write(input.readNBytes(skip));
            }
        }

        output.write(input.readAllBytes());
        return byteArrayOutputStream.toByteArray();

    }
}
