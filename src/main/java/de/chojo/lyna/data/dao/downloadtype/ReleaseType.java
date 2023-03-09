package de.chojo.lyna.data.dao.downloadtype;

import de.chojo.lyna.util.Enums;

public enum ReleaseType {
    STABLE, DEV, SNAPSHOT;

    public static ReleaseType parse(String string) {
        return Enums.parse(ReleaseType.class, string).get();
    }
}
