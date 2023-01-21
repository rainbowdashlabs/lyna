package de.chojo.lyna.configuration.elements;

import de.chojo.lyna.util.LicenseCreator;

public class License {
    private String baseSeed = LicenseCreator.generateRandomSequence(50);

    public long baseSeed() {
        return baseSeed.hashCode();
    }
}
