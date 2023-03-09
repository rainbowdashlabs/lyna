package de.chojo.lyna.util;

import java.util.Optional;

public class Enums {
    private Enums() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    public static <T extends Enum<T>> Optional<T> parse(Class<T> clazz, String value) {
        for (T enumConstant : clazz.getEnumConstants()) {
            if (enumConstant.name().equalsIgnoreCase(value)) {
                return Optional.of(enumConstant);
            }
        }
        return Optional.empty();
    }
}
