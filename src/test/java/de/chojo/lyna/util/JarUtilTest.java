package de.chojo.lyna.util;


import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class JarUtilTest {

    @Test
    void testIt() throws IOException {
        Map<String, String> replacements = Map.of("%%__USER__%%", "testId", "%%__RESOURCE__%%", "testResource", "%%__NONCE__%%", "testNonce");

        var resource = JarUtilTest.class.getClassLoader().getResourceAsStream("UserData.class");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        resource.transferTo(byteArrayOutputStream);
        ByteArrayOutputStream byteArrayOutputStream1 = JarUtil.replaceStringInJar(byteArrayOutputStream.toByteArray(), replacements);

        Path path = Path.of("UserDataNew.class");
        Files.deleteIfExists(path);
        Files.write(path, byteArrayOutputStream1.toByteArray(), StandardOpenOption.CREATE);


    }
}
