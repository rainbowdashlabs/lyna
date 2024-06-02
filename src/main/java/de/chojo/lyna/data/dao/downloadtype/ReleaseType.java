package de.chojo.lyna.data.dao.downloadtype;

import de.chojo.lyna.util.Enums;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum ReleaseType implements Comparable<ReleaseType> {
    STABLE(Collections.emptySet()), DEV(Set.of(STABLE)), SNAPSHOT(Set.of(STABLE, DEV));

    private final Set<ReleaseType> descendants;

    ReleaseType(Set<ReleaseType> descendants) {
        HashSet<ReleaseType> d = new HashSet<>(descendants);
        d.add(this);
        this.descendants = Collections.unmodifiableSet(d);
    }


    public static ReleaseType parse(String string) {
        return Enums.parse(ReleaseType.class, string).get();
    }

    public Set<ReleaseType> descendants() {
        return descendants;
    }
}
