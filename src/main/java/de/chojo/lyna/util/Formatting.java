package de.chojo.lyna.util;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class Formatting {
    public static String humanReadableByteCountSI(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }

    public static String duration(Duration duration) {
        List<String> parts = new ArrayList<>();
        long days = duration.toDaysPart();
        int hours = duration.toHoursPart();
        int minutes = duration.toMinutesPart();

        if (days != 0) {
            parts.add(days == 1 ? "1 Day" : days + " Days");
        }
        if (hours != 0) {
            parts.add(hours == 1 ? "1 Hour" : hours + " Hours");
        }
        if (minutes != 0) {
            parts.add(minutes == 1 ? "1 Minute" : minutes + " Minutes");
        }
        return String.join(" ", parts);
    }

}
