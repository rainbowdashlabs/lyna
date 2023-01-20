package de.chojo.lyna.configuration.elements;

public class License {
    String baseSeed;

    public long baseSeed() {
        return baseSeed.hashCode();
    }
}
