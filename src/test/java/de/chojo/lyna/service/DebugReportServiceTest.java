/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.configuration.elements.DebugReports;
import de.chojo.lyna.feature.debug.entity.DebugEntryKind;
import de.chojo.lyna.feature.debug.entity.DebugReport;
import de.chojo.lyna.feature.debug.repository.DebugReportRepository;
import de.chojo.lyna.feature.debug.service.DebugReportService;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A debug report from upload to deletion, as eldo-util's {@code DebugUtil} sends it.
 */
class DebugReportServiceTest extends RepositoryTestBase {
    private static final String REPORT = """
            {
              "pluginMeta": {"name": "BloodNight", "version": "1.2.3", "enabled": true, "main": "x.Main",
                             "authors": ["a"], "loadBefore": [], "dependencies": [], "softDependencies": [], "provides": []},
              "serverMeta": {"version": "Paper 1.21", "currentPlayers": 3, "loadedWorlds": ["world"], "plugins": []},
              "additionalPluginMeta": [{"name": "Hooks", "content": "PlaceholderAPI: yes"}],
              "latestLog": {"log": "[12:00:00 INFO]: Done\\n[12:00:01 WARN]: Hm", "pluginLog": "[12:00:00 INFO]: [BloodNight] on",
                            "internalExceptions": ["java.lang.NullPointerException\\n\\tat x.Main.run(Main.java:1)", ""],
                            "externalExceptions": []},
              "configDumps": [{"name": "plugins/BloodNight/config.yml", "content": "night: true"}],
              "v": 1
            }
            """;

    private DebugReportService service;

    @BeforeEach
    void setUp() throws SQLException {
        clear("debug_report_entry", "debug_report");
        service = new DebugReportService(new DebugReportRepository(), new DebugReports());
    }

    @Test
    @DisplayName("An upload is read back with its sections in the order they are shown")
    void uploadIsReadBack() {
        var keys = service.submit(REPORT).orElseThrow();

        DebugReport report = service.read(keys.hash()).orElseThrow();

        assertEquals("BloodNight", report.pluginName());
        assertEquals("1.2.3", report.pluginVersion());
        assertEquals(Duration.ofDays(14), Duration.between(report.created(), report.expires()));
        assertTrue(report.serverMeta().contains("Paper 1.21"));
        assertEquals(
                List.of(DebugEntryKind.META, DebugEntryKind.LOG, DebugEntryKind.PLUGIN_LOG,
                        DebugEntryKind.INTERNAL_EXCEPTION, DebugEntryKind.CONFIG),
                report.sections().stream().map(DebugReport.Section::kind).toList());
        assertEquals("plugins/BloodNight/config.yml", report.sections().get(4).name());
        assertEquals(2, report.sections().get(1).lines());
    }

    @Test
    @DisplayName("A section's content is found by the read key and its position")
    void sectionContent() {
        var keys = service.submit(REPORT).orElseThrow();

        assertEquals("[12:00:00 INFO]: Done\n[12:00:01 WARN]: Hm", service.section(keys.hash(), 1).orElseThrow());
        assertTrue(service.section(keys.hash(), 99).isEmpty());
        assertTrue(service.section(keys.deletionHash(), 1).isEmpty());
    }

    @Test
    @DisplayName("Keys are long, random and distinct, and the delete key does not read")
    void keys() {
        var first = service.submit(REPORT).orElseThrow();
        var second = service.submit(REPORT).orElseThrow();

        assertEquals(64, first.hash().length());
        assertNotEquals(first.hash(), second.hash());
        assertNotEquals(first.hash(), first.deletionHash());
        assertTrue(service.read(first.deletionHash()).isEmpty());
    }

    @Test
    @DisplayName("The delete key deletes the report and its sections, once")
    void deleteKeyDeletes() {
        var keys = service.submit(REPORT).orElseThrow();

        assertFalse(service.delete(keys.hash()));
        assertTrue(service.delete(keys.deletionHash()));
        assertFalse(service.delete(keys.deletionHash()));
        assertTrue(service.read(keys.hash()).isEmpty());
        assertTrue(service.section(keys.hash(), 0).isEmpty());
    }

    @Test
    @DisplayName("A NUL in a log is dropped rather than failing the upload")
    void nulIsDropped() {
        var keys = service.submit(REPORT.replace("Done", "Do\\u0000ne")).orElseThrow();

        assertTrue(service.section(keys.hash(), 1).orElseThrow().startsWith("[12:00:00 INFO]: Done"));
    }

    @Test
    @DisplayName("An old client's report without a plugin log or extras is still taken")
    void sparseReport() {
        var keys = service.submit("""
                {"pluginMeta": {"name": "Old"}, "latestLog": {"log": "x"}}
                """).orElseThrow();

        var report = service.read(keys.hash()).orElseThrow();
        assertEquals("unknown", report.pluginVersion());
        assertEquals(List.of(DebugEntryKind.LOG), report.sections().stream().map(DebugReport.Section::kind).toList());
    }

    @Test
    @DisplayName("What is not a report is refused")
    void notAReport() {
        assertTrue(service.submit("not json").isEmpty());
        assertTrue(service.submit("{}").isEmpty());
        assertTrue(service.submit("[]").isEmpty());
        assertTrue(service.submit("{\"pluginMeta\": \"x\"}").isEmpty());
    }

    @Test
    @DisplayName("Reports past the retention expire, newer ones stay")
    void expiry() throws SQLException {
        var old = service.submit(REPORT).orElseThrow();
        var fresh = service.submit(REPORT).orElseThrow();
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("UPDATE %s.debug_report SET created = now() - INTERVAL '15 days' WHERE read_key = '%s'"
                    .formatted(schemaName, old.hash()));
        }

        assertEquals(1, service.expire());
        assertTrue(service.read(old.hash()).isEmpty());
        assertTrue(service.read(fresh.hash()).isPresent());
    }
}
