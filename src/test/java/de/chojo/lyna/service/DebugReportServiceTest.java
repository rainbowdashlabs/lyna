/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.lyna.configuration.elements.DebugReports;
import de.chojo.lyna.feature.debug.entity.DebugEntryKind;
import de.chojo.lyna.feature.debug.entity.DebugReport;
import de.chojo.lyna.feature.debug.repository.DebugReportRepository;
import de.chojo.lyna.feature.debug.service.DebugReportService;
import de.chojo.lyna.feature.debug.service.DebugReportService.Keys;
import de.chojo.lyna.feature.debug.service.DebugReportService.Submitted;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    private Keys stored(String body) {
        return assertInstanceOf(Submitted.Stored.class, service.submit(body)).keys();
    }

    private boolean refused(String body) {
        return service.submit(body) instanceof Submitted.Refused;
    }

    @Test
    @DisplayName("An upload is read back with its sections in the order they are shown")
    void uploadIsReadBack() {
        var keys = stored(REPORT);

        DebugReport report = service.read(keys.hash()).orElseThrow();

        assertEquals("BloodNight", report.pluginName());
        assertEquals("1.2.3", report.pluginVersion());
        assertEquals(Duration.ofDays(14), Duration.between(report.created(), report.expires()));
        assertTrue(report.serverMeta().contains("Paper 1.21"));
        assertEquals(
                List.of(
                        DebugEntryKind.META,
                        DebugEntryKind.LOG,
                        DebugEntryKind.PLUGIN_LOG,
                        DebugEntryKind.INTERNAL_EXCEPTION,
                        DebugEntryKind.CONFIG),
                report.sections().stream().map(DebugReport.Section::kind).toList());
        assertEquals("plugins/BloodNight/config.yml", report.sections().get(4).name());
        assertEquals(2, report.sections().get(1).lines());
    }

    @Test
    @DisplayName("A section's content is found by the read key and its position")
    void sectionContent() {
        var keys = stored(REPORT);

        assertEquals(
                "[12:00:00 INFO]: Done\n[12:00:01 WARN]: Hm",
                service.section(keys.hash(), 1).orElseThrow());
        assertTrue(service.section(keys.hash(), 99).isEmpty());
        assertTrue(service.section(keys.deletionHash(), 1).isEmpty());
    }

    @Test
    @DisplayName("Keys are long, random and distinct, and the delete key does not read")
    void keys() {
        var first = stored(REPORT);
        var second = stored(REPORT);

        assertEquals(64, first.hash().length());
        assertNotEquals(first.hash(), second.hash());
        assertNotEquals(first.hash(), first.deletionHash());
        assertTrue(service.read(first.deletionHash()).isEmpty());
    }

    @Test
    @DisplayName("The delete key deletes the report and its sections, once")
    void deleteKeyDeletes() {
        var keys = stored(REPORT);

        assertFalse(service.delete(keys.hash()));
        assertTrue(service.delete(keys.deletionHash()));
        assertFalse(service.delete(keys.deletionHash()));
        assertTrue(service.read(keys.hash()).isEmpty());
        assertTrue(service.section(keys.hash(), 0).isEmpty());
    }

    @Test
    @DisplayName("A NUL in a log is dropped rather than failing the upload")
    void nulIsDropped() {
        var keys = stored(REPORT.replace("Done", "Do\\u0000ne"));

        assertTrue(service.section(keys.hash(), 1).orElseThrow().startsWith("[12:00:00 INFO]: Done"));
    }

    @Test
    @DisplayName("An old client's report without a plugin log or extras is still taken")
    void sparseReport() {
        var keys = stored("""
                {"pluginMeta": {"name": "Old"}, "latestLog": {"log": "x"}}
                """);

        var report = service.read(keys.hash()).orElseThrow();
        assertEquals("unknown", report.pluginVersion());
        assertEquals(
                List.of(DebugEntryKind.LOG),
                report.sections().stream().map(DebugReport.Section::kind).toList());
    }

    @Test
    @DisplayName("What is not a report is refused")
    void notAReport() {
        assertTrue(refused("not json"));
        assertTrue(refused("{}"));
        assertTrue(refused("[]"));
        assertTrue(refused("{\"pluginMeta\": \"x\"}"));
    }

    @Test
    @DisplayName("Reports past the retention expire, newer ones stay")
    void expiry() throws SQLException {
        var old = stored(REPORT);
        var fresh = stored(REPORT);
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("UPDATE %s.debug_report SET created = now() - INTERVAL '15 days' WHERE read_key = '%s'"
                    .formatted(schemaName, old.hash()));
        }

        assertEquals(1, service.expire());
        assertTrue(service.read(old.hash()).isEmpty());
        assertTrue(service.read(fresh.hash()).isPresent());
    }

    private static final String REPORT_V2 = """
            {
              "v": 2,
              "plugin": {"name": "BloodNight", "version": "2.0.0", "main": "x.Main"},
              "server": {"version": "Paper 26.1", "currentPlayers": 1},
              "sections": [
                {"kind": "LOG", "name": "latest.log", "content": "[12:00:00 INFO]: Done"},
                {"kind": "CONFIG", "name": "mobs.yml", "content": "password: hunter2"},
                {"kind": "META", "content": "free text"}
              ]
            }
            """;

    @Test
    @DisplayName("A report in the second format keeps its sections in the order sent, unaltered")
    void secondFormat() {
        var stored = assertInstanceOf(Submitted.Stored.class, service.submit(REPORT_V2));

        assertEquals(2, stored.format());
        assertTrue(stored.expires().isAfter(Instant.now().plus(Duration.ofDays(13))));
        DebugReport report = service.read(stored.keys().hash()).orElseThrow();
        assertEquals("BloodNight", report.pluginName());
        assertEquals("2.0.0", report.pluginVersion());
        assertTrue(report.serverMeta().contains("Paper 26.1"));
        assertEquals(
                List.of("latest.log", "mobs.yml", "META"),
                report.sections().stream().map(DebugReport.Section::name).toList());
        assertEquals(
                "password: hunter2", service.section(stored.keys().hash(), 1).orElseThrow());
    }

    @Test
    @DisplayName("A report in the first format is answered in the first format")
    void firstFormatAnswer() {
        assertEquals(
                1,
                assertInstanceOf(Submitted.Stored.class, service.submit(REPORT)).format());
    }

    @Test
    @DisplayName("A report in the second format is refused with a reason when it breaks a rule")
    void secondFormatRefusals() {
        assertReason("names no plugin", "{\"v\": 2}");
        assertReason("has no name", "{\"v\": 2, \"plugin\": {\"version\": \"1\"}}");
        assertReason("shape of format 2", "{\"v\": 2, \"plugin\": {\"name\": \"x\"}, \"sections\": 5}");
        assertReason("has no kind", "{\"v\": 2, \"plugin\": {\"name\": \"x\"}, \"sections\": [{\"name\": \"x\"}]}");
        assertReason("Section 0 is empty", "{\"v\": 2, \"plugin\": {\"name\": \"x\"}, \"sections\": [null]}");
        assertReason("longer than 200", REPORT_V2.replace("mobs.yml", "m".repeat(201)));
        String many = "{\"kind\": \"META\", \"content\": \"x\"},".repeat(DebugReportService.MAX_SECTIONS + 1);
        assertReason(
                "at most 200 sections",
                "{\"v\": 2, \"plugin\": {\"name\": \"x\"}, \"sections\": [%s]}"
                        .formatted(many.substring(0, many.length() - 1)));
    }

    private void assertReason(String expected, String body) {
        var refused = assertInstanceOf(Submitted.Refused.class, service.submit(body));
        assertTrue(refused.reason().contains(expected), refused.reason());
    }

    private static final String REPORT_WITH_HINTS = """
            {
              "v": 2,
              "plugin": {"name": "BloodNight", "version": "2.0.0"},
              "sections": [
                {"kind": "CHECK", "name": "Database", "content": "Reachable in 3 ms", "level": "OK"},
                {"kind": "META", "name": "Hooks", "entries": [{"key": "WorldGuard", "value": "7.0.9"}, {"key": "Vault"}]},
                {"kind": "INTERNAL_EXCEPTION", "name": "Plugin exception 1", "content": "npe", "count": 3,
                 "firstSeen": "12:00:01", "lastSeen": "12:30:00", "level": "ERROR", "entries": [{"key": "k", "value": "v"}]},
                {"kind": "CONFIG", "name": "mobs/zombie.yml", "content": "speed: 1", "format": "yaml", "count": 4}
              ]
            }
            """;

    @Test
    @DisplayName("Display hints are kept where they fit the kind and dropped where they do not")
    void displayHints() {
        var keys = stored(REPORT_WITH_HINTS);

        List<DebugReport.Section> sections =
                service.read(keys.hash()).orElseThrow().sections();

        assertEquals(
                new DebugReport.Display(null, "OK", null, null, null, null, null),
                sections.get(0).display());
        assertEquals(
                List.of(new DebugReport.Entry("WorldGuard", "7.0.9"), new DebugReport.Entry("Vault", "")),
                sections.get(1).display().entries());
        assertEquals(
                new DebugReport.Display(null, null, null, 3, "12:00:01", "12:30:00", null),
                sections.get(2).display());
        assertEquals(
                new DebugReport.Display("yaml", null, null, null, null, null, null),
                sections.get(3).display());
        assertEquals(DebugEntryKind.CHECK, sections.get(0).kind());
    }

    @Test
    @DisplayName("Display hints that make no sense are refused with a reason")
    void displayHintRefusals() {
        String one = "{\"v\": 2, \"plugin\": {\"name\": \"x\"}, \"sections\": [%s]}";
        assertReason("unknown format xml", one.formatted("{\"kind\": \"CONFIG\", \"format\": \"xml\"}"));
        assertReason("needs a level", one.formatted("{\"kind\": \"CHECK\", \"name\": \"Db\"}"));
        assertReason("needs a level", one.formatted("{\"kind\": \"CHECK\", \"level\": \"FINE\"}"));
        assertReason("entry without a key", one.formatted("{\"kind\": \"META\", \"entries\": [{\"value\": \"v\"}]}"));
        assertReason("a count is at least 1", one.formatted("{\"kind\": \"INTERNAL_EXCEPTION\", \"count\": 0}"));
        assertReason(
                "time longer than",
                one.formatted("{\"kind\": \"INTERNAL_EXCEPTION\", \"firstSeen\": \"%s\"}"
                        .formatted("1".repeat(DebugReportService.MAX_TIME + 1))));
        String entries = "{\"key\": \"k\"},".repeat(DebugReportService.MAX_ENTRIES + 1);
        assertReason(
                "more than 200 entries",
                one.formatted("{\"kind\": \"META\", \"entries\": [%s]}"
                        .formatted(entries.substring(0, entries.length() - 1))));
    }

    @Test
    @DisplayName("Sections of an unknown kind are left out and named in a metadata section")
    void unknownKindsAreSkipped() {
        var keys = stored(REPORT_V2.replace("\"CONFIG\"", "\"BLOB\""));

        List<DebugReport.Section> sections =
                service.read(keys.hash()).orElseThrow().sections();

        assertEquals(
                List.of("latest.log", "META", "Skipped sections"),
                sections.stream().map(DebugReport.Section::name).toList());
        assertEquals(
                List.of(new DebugReport.Entry("BLOB", "1 section(s)")),
                sections.get(2).display().entries());
    }

    private static final String CHART = """
            {"type": "line", "unit": "ms", "data": {"render": [1, "2.5", null], "budget": [[0, 25], {"x": 1, "y": 25}]},
             "custom": {"anything": true}}
            """;

    @Test
    @DisplayName("A chart is kept as it was sent, whatever its data look like")
    void chartIsKept() throws Exception {
        var keys = stored("""
                {"v": 2, "plugin": {"name": "x"}, "sections": [
                  {"kind": "CHART", "name": "Render time", "chart": %s},
                  {"kind": "META", "name": "Hooks", "chart": %s}
                ]}
                """.formatted(CHART, CHART));

        List<DebugReport.Section> sections =
                service.read(keys.hash()).orElseThrow().sections();

        assertEquals(DebugEntryKind.CHART, sections.get(0).kind());
        assertEquals(
                new ObjectMapper().readTree(CHART), sections.get(0).display().chart());
        assertEquals(null, sections.get(1).display().chart());
    }

    @Test
    @DisplayName("A chart of a type the viewer does not draw is left out and named in a metadata section")
    void unknownChartTypesAreSkipped() {
        var keys = stored("""
                {"v": 2, "plugin": {"name": "x"}, "sections": [
                  {"kind": "CHART", "name": "Spider", "chart": {"type": "radar", "data": {"speed": 3}}},
                  {"kind": "CHART", "name": "Line", "chart": {"type": "line", "data": [1, 2]}}
                ]}
                """);

        List<DebugReport.Section> sections =
                service.read(keys.hash()).orElseThrow().sections();

        assertEquals(
                List.of("Line", "Skipped sections"),
                sections.stream().map(DebugReport.Section::name).toList());
        assertEquals(
                List.of(new DebugReport.Entry("CHART radar", "1 section(s)")),
                sections.get(1).display().entries());
    }

    @Test
    @DisplayName("A chart that breaks a limit is refused with a reason")
    void chartRefusals() {
        String one = "{\"v\": 2, \"plugin\": {\"name\": \"x\"}, \"sections\": [{\"kind\": \"CHART\", \"chart\": %s}]}";
        assertReason(
                "needs a chart object",
                "{\"v\": 2, \"plugin\": {\"name\": \"x\"}, \"sections\": [{\"kind\": \"CHART\"}]}");
        assertReason("needs a type", one.formatted("{\"data\": [1]}"));
        assertReason(
                "needs a type",
                one.formatted("{\"type\": \"%s\", \"data\": [1]}"
                        .formatted("t".repeat(DebugReportService.MAX_CHART_TYPE + 1))));
        assertReason("needs data", one.formatted("{\"type\": \"line\"}"));
        assertReason("nested deeper", one.formatted("{\"type\": \"line\", \"data\": [[[[[[1]]]]]]}"));
        String values = "1,".repeat(DebugReportService.MAX_CHART_VALUES + 1);
        assertReason(
                "more than 20000 values",
                one.formatted(
                        "{\"type\": \"line\", \"data\": [%s]}".formatted(values.substring(0, values.length() - 1))));
        String label = "\"%s\",".formatted("l".repeat(1000)).repeat(70);
        assertReason(
                "larger than",
                one.formatted(
                        "{\"type\": \"line\", \"data\": [%s]}".formatted(label.substring(0, label.length() - 1))));
    }
}
