package de.chojo.lyna.util;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;

public class JarUtilTest {

    @Test
    void testIt() throws IOException {
        Map<String, String> replacements = Map.of("%%__USER__%%", "testId", "%%__RESOURCE__%%", "testResource", "%%__NONCE__%%", "testNonce");

        var userData = JarUtilTest.class.getClassLoader().getResourceAsStream("UserData.class");
        var out = JarUtil.replaceStringInJar(read(userData), replacements);

        var replacedUserData = JarUtil.class.getClassLoader().getResourceAsStream("ReplacedUserData.class");
        Assertions.assertArrayEquals(out.toByteArray(), read(replacedUserData));
    }

    private byte[] read(InputStream inputStream) throws IOException {
        return inputStream.readAllBytes();
    }
}
