package de.chojo.lyna.configuration;

import dev.chojo.ocular.Configurations;
import dev.chojo.ocular.dataformats.JsonDataFormat;
import dev.chojo.ocular.dataformats.YamlDataFormat;
import dev.chojo.ocular.key.Key;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Reads the configuration out of a directory, and writes it back when something is missing.
 *
 * <p>Values may come from the environment instead of the file. Every element carries
 * {@code @Overwrite(env = @Env)}, so a password belongs in the environment of the process and the
 * file holds a blank - which is the reason this exists rather than the format it happens to use.
 */
public class Conf extends Configurations<ConfigFile> {
    private static final Logger log = getLogger(Conf.class);

    public static final Key<ConfigFile> CONFIG =
            Key.builder(Path.of("config.yaml"), ConfigFile::new).build();

    /**
     * Where the configuration lived before this read YAML. Kept only to be read once.
     */
    private static final Key<ConfigFile> LEGACY_JSON =
            Key.builder(Path.of("config.json"), ConfigFile::new).build();

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
        super(directory, CONFIG, List.of(new YamlDataFormat(), new JsonDataFormat()),
                Conf.class.getClassLoader(), null);
        adoptLegacyJson();
    }

    /**
     * Takes over a {@code config.json} left by an older version.
     *
     * <p>Without this an upgrade is silent rather than loud: Ocular writes defaults for a file it
     * cannot find instead of refusing, so a deployment that kept its JSON would start successfully on
     * a bot with no token and a database on localhost.
     *
     * <p>The JSON is read and a {@code config.yaml} written beside it. The old file is left where it
     * is - this has no business deleting the only copy of somebody's settings, and its presence is
     * harmless once the YAML exists.
     */
    private void adoptLegacyJson() {
        if (exists(CONFIG) || !exists(LEGACY_JSON)) return;
        migrate(LEGACY_JSON, CONFIG);
        log.info("Read the configuration from {} and wrote {} beside it. The old file is no longer "
                        + "read and can be removed.",
                LEGACY_JSON.path(), CONFIG.path());
    }
}
