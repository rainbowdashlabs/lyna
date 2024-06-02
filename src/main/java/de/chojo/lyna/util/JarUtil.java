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

    public static void main(String[] args) {
        Path path = Path.of("de", "eldoria", "schematicbrush", "libs", "eldoutilities", "debug", "UserData.class");
    }

    // Made by goldmensch (contact goldmensch on discord, or write email to nickhensel25@icloud.com if it breaks)
    public static ByteArrayOutputStream replaceStringInJar(byte[] bytes, Map<String, String> replacements) throws IOException {
        OpenByteArrayInputStream byteArrayInputStream = new OpenByteArrayInputStream(bytes);
        DataInputStream input = new DataInputStream(byteArrayInputStream);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(byteArrayOutputStream);

        outputStream.write(bytes, 0, 8);
        input.skipBytes(8);

        int poolCount = input.readUnsignedShort() - 1;
        outputStream.writeShort((poolCount + 1) & 0xFFFF);

        for (int i = 0; i < poolCount; i++) {
            int tag = input.readUnsignedByte();
            outputStream.write(tag);
            if (tag == 1) {
                String str = input.readUTF();

                for (var entry : replacements.entrySet()) {
                    if (str.equals(entry.getKey())) {
                        System.out.println("yeah " + entry.getKey());
                        System.out.println(entry.getValue());
                        outputStream.writeUTF(entry.getValue());
                    }
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
                outputStream.write(bytes, byteArrayInputStream.pos()-1, skip-1);
                input.skipBytes(skip-1);
            }
        }
        outputStream.write(bytes, byteArrayInputStream.pos()-1, input.available());
        return byteArrayOutputStream;

    }
}
