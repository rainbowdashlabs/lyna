package de.chojo.lyna.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading the configuration out of a directory.
 *
 * <p>Most of this is about the upgrade. Ocular writes defaults for a file it cannot find rather than
 * refusing, so a deployment whose JSON stopped being read would start successfully and quietly be
 * somebody else's application - a bot with no token, pointed at a database on localhost.
 */
class ConfTest {

    @Test
    @DisplayName("An empty directory yields the defaults, and writes them down")
    void emptyDirectoryWritesDefaults(@TempDir Path directory) {
        Conf conf = new Conf(directory);

        assertEquals(5, conf.main().database().poolSize());
        assertTrue(Files.exists(directory.resolve("config.yaml")));
    }

    @Test
    @DisplayName("A config.yaml is read back as it was written")
    void yamlIsRead(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("config.yaml"), """
                database:
                  host: "written-by-hand"
                  poolSize: 11
                """);

        Conf conf = new Conf(directory);

        assertEquals("written-by-hand", conf.main().database().host());
        assertEquals(11, conf.main().database().poolSize());
    }

    @Test
    @DisplayName("A directory holding only the old JSON is read, and gains a config.yaml")
    void legacyJsonIsAdopted(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("config.json"), """
                {
                  "database" : {
                    "host" : "from-the-old-file",
                    "poolSize" : 9
                  }
                }
                """);

        Conf conf = new Conf(directory);

        assertEquals("from-the-old-file", conf.main().database().host());
        assertEquals(9, conf.main().database().poolSize());
        assertTrue(Files.exists(directory.resolve("config.yaml")),
                "the values should have been written on in the format that is now read");
        assertTrue(Files.readString(directory.resolve("config.yaml")).contains("from-the-old-file"));
    }

    @Test
    @DisplayName("The old file is left alone rather than deleted")
    void legacyJsonSurvivesTheAdoption(@TempDir Path directory) throws IOException {
        String json = """
                {"database" : {"host" : "keep-me"}}
                """;
        Files.writeString(directory.resolve("config.json"), json);

        new Conf(directory);

        assertTrue(Files.exists(directory.resolve("config.json")));
        assertEquals(json, Files.readString(directory.resolve("config.json")));
    }

    @Test
    @DisplayName("Once a config.yaml exists the old JSON is ignored, whatever it still says")
    void yamlWinsOverLegacyJson(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("config.json"), """
                {"database" : {"host" : "the-stale-one"}}
                """);
        Files.writeString(directory.resolve("config.yaml"), """
                database:
                  host: "the-current-one"
                """);

        Conf conf = new Conf(directory);

        assertEquals("the-current-one", conf.main().database().host());
    }

    @Test
    @DisplayName("Adopting the old file twice is the same as adopting it once")
    void adoptionIsIdempotent(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("config.json"), """
                {"database" : {"host" : "once-and-once-only", "poolSize" : 7}}
                """);

        new Conf(directory);
        Conf second = new Conf(directory);

        assertEquals("once-and-once-only", second.main().database().host());
        assertEquals(7, second.main().database().poolSize());
    }

    @Test
    @DisplayName("Nothing is adopted when there was nothing there")
    void noLegacyFileMeansNoAdoption(@TempDir Path directory) {
        new Conf(directory);

        assertFalse(Files.exists(directory.resolve("config.json")));
    }

    @Test
    @DisplayName("A property beats what the file says, which is how a secret stays out of the file")
    void propertyOverridesTheFile(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("config.yaml"), """
                database:
                  host: "from-the-file"
                  password: "in-the-file"
                """);

        System.setProperty("db.password", "from-the-environment");
        try {
            Conf conf = new Conf(directory);

            assertEquals("from-the-environment", conf.main().database().password());
            assertEquals("from-the-file", conf.main().database().host());
        } finally {
            System.clearProperty("db.password");
        }
    }

    @Test
    @DisplayName("An override reaches a nested element under its own prefix")
    void nestedElementsHaveTheirOwnPrefix(@TempDir Path directory) {
        System.setProperty("discord.oauth.clientSecret", "not-in-any-file");
        try {
            Conf conf = new Conf(directory);

            assertEquals("not-in-any-file", conf.main().discord().oauth().clientSecret());
        } finally {
            System.clearProperty("discord.oauth.clientSecret");
        }
    }

    /**
     * The guarantee that makes overrides worth having: a value supplied from outside is used but not
     * recorded. Ocular writes the file before it applies the overrides, so this holds as long as
     * nothing calls {@link Conf#save()} afterwards - which is why nothing does.
     */
    @Test
    @DisplayName("A value supplied from outside is used but never written into the file")
    void overridesAreNotWrittenBack(@TempDir Path directory) throws IOException {
        System.setProperty("db.password", "never-write-me-down");
        try {
            Conf conf = new Conf(directory);

            assertEquals("never-write-me-down", conf.main().database().password());
        } finally {
            System.clearProperty("db.password");
        }

        String written = Files.readString(directory.resolve("config.yaml"));
        assertTrue(written.contains("password"), "the file should still carry the key");
        assertFalse(written.contains("never-write-me-down"),
                "a secret supplied from outside the file must not be saved into it");
    }

    @Test
    @DisplayName("Adopting the old JSON does not write an override into the new file either")
    void adoptionDoesNotCarryAnOverrideIntoTheFile(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("config.json"), """
                {"database" : {"password" : "was-in-the-old-file"}}
                """);

        System.setProperty("db.password", "supplied-from-outside");
        try {
            Conf conf = new Conf(directory);

            assertEquals("supplied-from-outside", conf.main().database().password());
        } finally {
            System.clearProperty("db.password");
        }

        assertFalse(Files.readString(directory.resolve("config.yaml")).contains("supplied-from-outside"));
    }
}
