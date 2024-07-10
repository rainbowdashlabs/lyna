package de.chojo.lyna.web.downloads;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.lyna.configuration.ConfigFile;

public class Download {
    private String downloadJs = null;
    private final Configuration<ConfigFile> configuration;

    public Download(Configuration<ConfigFile> configuration) {
        this.configuration = configuration;
    }

    public void init() {
    }
}
