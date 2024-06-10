package de.chojo.lyna.util;

import java.io.*;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class JarUtil {

    // Made by goldmensch (contact goldmensch on discord, or write email to nickhensel25@icloud.com if it breaks)
    public static byte[] replaceStringsInJar(InputStream stream, Map<String, String> replacements) throws IOException {
        var bytesOut = new ByteArrayOutputStream();
        try(var outputZip = new ZipOutputStream(bytesOut); var inputZip = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = inputZip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    outputZip.putNextEntry(entry);
                    if (entry.getName().endsWith(".class")) {
                        byte[] bytes = replaceStringsInClass(inputZip, replacements);
                        outputZip.write(bytes);
                    } else {
                        inputZip.transferTo(outputZip);
                    }

                    outputZip.closeEntry();
                }
            }
        }
        return bytesOut.toByteArray();
    }

    // Made by goldmensch (contact goldmensch on discord, or write email to nickhensel25@icloud.com if it breaks)
    public static byte[] replaceStringsInClass(InputStream inputStream, Map<String, String> replacements) throws IOException {
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

                var out = replacements.getOrDefault(str, str);
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
