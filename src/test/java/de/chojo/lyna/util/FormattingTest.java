package de.chojo.lyna.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FormattingTest {

    @Test
    void duration() {
        Assertions.assertEquals("1 Minute",Formatting.duration(Duration.ofMinutes(1)));
        Assertions.assertEquals("2 Minutes",Formatting.duration(Duration.ofMinutes(2)));
        Assertions.assertEquals("1 Hour",Formatting.duration(Duration.ofHours(1)));
        Assertions.assertEquals("2 Hours",Formatting.duration(Duration.ofHours(2)));
        Assertions.assertEquals("1 Day",Formatting.duration(Duration.ofDays(1)));
        Assertions.assertEquals("2 Days",Formatting.duration(Duration.ofDays(2)));
        System.out.println("⏹\uFE0F");
    }
}
