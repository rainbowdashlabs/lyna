package de.chojo.lyna.util;

import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Version(String version, List<Integer> nums, ReleaseType type) implements Comparable<Version> {
    private static final Pattern NUMBER = Pattern.compile("([0-9]+)");


    public static Version parse(String version) {
        List<Integer> nums = new ArrayList<>();

        Matcher matcher = NUMBER.matcher(version);
        while (matcher.find()) {
            nums.add(Integer.parseInt(matcher.group(1)));
        }

        ReleaseType type = ReleaseType.STABLE;

        if (version.toLowerCase(Locale.ROOT).contains("snapshot")) {
            type = ReleaseType.SNAPSHOT;
        } else if (version.toLowerCase(Locale.ROOT).contains("dev")) {
            type = ReleaseType.DEV;
        }

        return new Version(version, nums, type);
    }

    @Override
    public List<Integer> nums() {
        return Collections.unmodifiableList(nums);
    }

    public boolean isOlder(Version version) {
        return compareTo(version) < 0;
    }

    public boolean isNewer(Version version) {
        return compareTo(version) > 0;
    }

    public Comparator<String> comparator() {
        return Comparator.comparing(Version::parse);
    }

    @Override
    public int compareTo(@NotNull Version version) {
        int numbers = Math.max(version.nums().size(), nums().size());
        for (int i = 0; i < numbers; i++) {
            int compare = Integer.compare(nums().size() > i ? nums().get(i) : 0,
                    version.nums().size() > i ? version.nums().get(i) : 0);
            if (compare != 0) return compare;
        }
        return version.type().compareTo(type());
    }
}
