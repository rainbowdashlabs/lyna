package de.chojo.lyna.configuration.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.Prop;
import dev.chojo.ocular.override.OverwritePrefix;

import de.chojo.lyna.util.LicenseCreator;

@OverwritePrefix("LICENSE")
public class License {
    @Overwrite(env = @Env, prop = @Prop)
    private String baseSeed = LicenseCreator.generateRandomSequence(50);

    public long baseSeed() {
        return baseSeed.hashCode();
    }
}
