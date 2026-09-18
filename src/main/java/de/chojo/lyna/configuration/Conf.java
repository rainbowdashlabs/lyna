/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.configuration;

import dev.chojo.ocular.Configurations;
import dev.chojo.ocular.dataformats.YamlDataFormat;
import dev.chojo.ocular.key.Key;
import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Reads the configuration out of a directory, and writes it back when it is not there.
 *
 * <p>Values may come from outside the file: every element carries {@code @Overwrite}, so a password
 * belongs in the environment of the process and the file holds a blank. That is what this is for,
 * rather than the format it happens to use.
 *
 * <p><strong>Nothing may call {@link #save()} on a loaded configuration.</strong> Ocular applies the
 * overrides into the same object it would serialise, so saving writes whatever the environment
 * supplied into the file - which is the one thing overrides exist to avoid. The file is written when
 * it is missing, before any override is applied, and that is the only write there should be.
 */
public class Conf extends Configurations<ConfigFile> {
    private static final Logger log = getLogger(Conf.class);

    public static final Key<ConfigFile> CONFIG =
            Key.builder(Path.of("config.yaml"), ConfigFile::new).build();

    /** What the file was called before this read YAML. */
    private static final String LEGACY_JSON = "config.json";

    public Conf() {
        this(Path.of("config"));
    }

    /**
     * Reads the configuration from a directory of the caller's choosing.
     *
     * <p>The application always uses {@code config/} beside itself. A test names its own directory
     * instead, so what it reads is the configuration it wrote rather than whatever the machine
     * running it happens to hold.
     */
    public Conf(Path directory) {
        super(
                fillInMissingKeys(adoptLegacyJson(directory)),
                CONFIG,
                List.of(new YamlDataFormat()),
                Conf.class.getClassLoader(),
                null);
    }

    /**
     * Turns a {@code config.json} left by an older version into the {@code config.yaml} that is now
     * read, if that has not happened already.
     *
     * <p>Without this an upgrade is silent rather than loud: Ocular writes defaults for a file it
     * cannot find instead of refusing, so a deployment that kept its JSON would start successfully on
     * a bot with no token and a database on localhost.
     *
     * <p>Done as a copy from one document to the other rather than by loading the configuration and
     * saving it again. Loading applies the overrides, and saving would then write whatever the
     * environment supplied into the new file - turning an upgrade into the moment a password got
     * committed to disk.
     *
     * <p>The old file is left where it is. This has no business deleting the only copy of somebody's
     * settings, and its presence is harmless once the YAML exists.
     *
     * @param directory the directory holding the configuration
     * @return that same directory, now certainly holding a {@code config.yaml} if it held a JSON one
     */
    private static Path adoptLegacyJson(Path directory) {
        Path yaml = directory.resolve(CONFIG.path());
        Path json = directory.resolve(LEGACY_JSON);
        if (Files.exists(yaml) || !Files.exists(json)) return directory;
        try {
            JsonNode tree = JsonMapper.builder().build().readTree(Files.readString(json));
            Files.createDirectories(directory);
            Files.writeString(yaml, YAMLMapper.builder().build().writeValueAsString(tree));
            log.info(
                    "Read the configuration from {} and wrote {} beside it. The old file is no "
                            + "longer read and can be removed.",
                    LEGACY_JSON,
                    CONFIG.path());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "Found %s but could not turn it into %s. Lyna now reads YAML; convert the file by "
                                    .formatted(json, yaml)
                            + "hand, or move it aside to start on defaults.",
                    e);
        }
        return directory;
    }

    /**
     * Adds the settings a newer version understands to a file written by an older one.
     *
     * <p>Ocular writes the file when it is missing and never again, so an instance that has been
     * running since before a setting existed has no line for it and no way to learn that it should.
     * That is how an upgrade arrives with the sign-in secret and the Discord credentials silently
     * absent: the values fall back to blanks, and nothing says so until somebody tries to use them.
     *
     * <p>Done as a merge of one document into another, for the same reason {@link #adoptLegacyJson}
     * is: the loaded configuration carries whatever the environment supplied, and writing that back
     * is how a password ends up on disk. This reads the file, takes the defaults from a fresh
     * {@link ConfigFile} that no override has touched, and writes back only the keys the file did not
     * have. What is already there is never changed.
     *
     * @param directory the directory holding the configuration
     * @return that same directory
     */
    private static Path fillInMissingKeys(Path directory) {
        Path yaml = directory.resolve(CONFIG.path());
        if (!Files.exists(yaml)) return directory;
        try {
            // The same visibility Ocular reads and writes with: these classes keep their settings in
            // private fields and offer no setters, so a mapper left on its defaults sees an empty
            // object and would decide that nothing is missing.
            YAMLMapper mapper = YAMLMapper.builder()
                    .changeDefaultVisibility(checker -> checker.withFieldVisibility(
                                    com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
                            .withGetterVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE))
                    .build();
            JsonNode existing = mapper.readTree(Files.readString(yaml));
            if (!existing.isObject()) return directory;
            JsonNode defaults = mapper.valueToTree(new ConfigFile());
            List<String> added = new java.util.ArrayList<>();
            merge((tools.jackson.databind.node.ObjectNode) existing, defaults, "", added);
            if (added.isEmpty()) return directory;
            Files.writeString(yaml, mapper.writeValueAsString(existing));
            log.info(
                    "Added {} setting(s) this version understands to {}: {}",
                    added.size(),
                    CONFIG.path(),
                    String.join(", ", added));
        } catch (IOException | RuntimeException e) {
            log.warn(
                    "Could not add the newer settings to {}. Anything missing falls back to its "
                            + "default, which for a secret means it is not set.",
                    yaml,
                    e);
        }
        return directory;
    }

    /**
     * Copies into {@code target} the keys {@code defaults} has and it does not, recursing into
     * objects so a section that exists but lacks a setting gains only that setting.
     *
     * @param added the paths that were added, for saying so afterwards
     */
    private static void merge(
            tools.jackson.databind.node.ObjectNode target, JsonNode defaults, String path, List<String> added) {
        defaults.propertyNames().forEach(name -> {
            JsonNode fallback = defaults.get(name);
            String here = path.isEmpty() ? name : path + "." + name;
            JsonNode present = target.get(name);
            if (present == null) {
                target.set(name, fallback);
                added.add(here);
            } else if (present.isObject() && fallback.isObject()) {
                merge((tools.jackson.databind.node.ObjectNode) present, fallback, here, added);
            }
        });
    }
}
