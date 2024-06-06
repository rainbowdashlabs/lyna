package de.chojo.lyna.util;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class JarUtilTest {

    Map<String, String> replacements = Map.of("%%__USER__%%", "testId", "%%__RESOURCE__%%", "testResource", "%%__NONCE__%%", "testNonce");

    @Test
    void testClass() throws IOException {
        var userData = JarUtilTest.class.getClassLoader().getResourceAsStream("UserData.class");
        var out = JarUtil.replaceStringsInClass(userData, replacements);

        var replacedUserData = JarUtil.class.getClassLoader().getResourceAsStream("ReplacedUserData.class");
        Assertions.assertArrayEquals(out, replacedUserData.readAllBytes());
    }

    @Test
    void testJar() throws IOException {
        InputStream testJar = JarUtil.class.getClassLoader().getResourceAsStream("TestJar.jar");
        var replaced = JarUtil.replaceStringsInJar(testJar, replacements);

        var replacedTestJar = JarUtil.class.getClassLoader().getResourceAsStream("ReplacedTestJar.jar");
        Assertions.assertArrayEquals(replaced, replacedTestJar.readAllBytes());
    }
}
