/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.configuration.elements.DebugReports;
import de.chojo.lyna.feature.debug.entity.ChartType;
import de.chojo.lyna.feature.debug.entity.DebugEntryKind;
import de.chojo.lyna.feature.debug.entity.DebugReport;
import de.chojo.lyna.feature.debug.entity.DebugReport.Display;
import de.chojo.lyna.feature.debug.entity.DebugReport.NewSection;
import de.chojo.lyna.feature.debug.entity.DebugSubmission;
import de.chojo.lyna.feature.debug.entity.DebugSubmissionV2;
import de.chojo.lyna.feature.debug.repository.DebugReportRepository;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Takes debug reports from plugins and hands them to whoever holds the link.
 *
 * <p>Keys are 32 random bytes. Whoever has the read key may read the report, so it must not be
 * guessable; UpdateButler derived its keys from the clock. The delete key is kept only as a hash,
 * which is enough to recognise it and useless to anyone reading the database.
 *
 * <p>Two formats are taken. The first is UpdateButler's, still sent by deployed plugins; the second hands over the
 * sections themselves, so the client decides what a report holds. Redacting is the client's job: what arrives is
 * stored as it is. Sections of a kind this service does not know are left out, and a metadata section says which; a
 * plugin built for a newer viewer thus never loses its whole report.
 *
 * <p>A chart is taken with whatever data it brings; only its size is limited. Its type must be one the viewer draws
 * ({@link ChartType}), a chart of another type is left out like a section of an unknown kind. The viewer shows the data
 * as it is when it cannot draw it.
 *
 * <p>PostgreSQL text cannot hold a NUL character, and logs sometimes do, so those are dropped.
 */
@Singleton
public class DebugReportService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    public static final int MAX_SECTIONS = 200;
    public static final int MAX_NAME = 200;
    public static final int MAX_ENTRIES = 200;
    public static final int MAX_TIME = 32;
    public static final int MAX_CHART_TYPE = 32;
    public static final int MAX_CHART_BYTES = 64 * 1024;
    public static final int MAX_CHART_DEPTH = 5;
    public static final int MAX_CHART_VALUES = 20_000;

    private final DebugReportRepository reports;
    private final DebugReports settings;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * Field names are what the deployed clients read with Gson; they cannot change.
     *
     * @param hash         the read key
     * @param deletionHash the delete key
     */
    public record Keys(String hash, String deletionHash) {}

    @Inject
    public DebugReportService(DebugReportRepository reports, DebugReports settings) {
        this.reports = reports;
        this.settings = settings;
    }

    /**
     * What became of an upload.
     */
    public sealed interface Submitted {
        /**
         * @param format the format the report came in, which decides the shape of the answer
         */
        record Stored(Keys keys, Instant expires, int format) implements Submitted {}

        /**
         * @param reason what is wrong with it, for whoever sent it
         */
        record Refused(String reason) implements Submitted {}
    }

    /**
     * Stores a report in either format: the second when the body says {@code "v": 2}, the first otherwise.
     */
    public Submitted submit(String body) {
        JsonNode tree;
        try {
            tree = withoutNul(json.readTree(body));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return new Submitted.Refused("The body is not JSON.");
        }
        if (tree == null || !tree.isObject()) return new Submitted.Refused("The body is not a JSON object.");
        return tree.path("v").asInt(1) == 2 ? submitV2(tree) : submitV1(tree);
    }

    private Submitted submitV1(JsonNode tree) {
        DebugSubmission submission;
        try {
            submission = json.treeToValue(tree, DebugSubmission.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return new Submitted.Refused("That is not a debug report.");
        }
        if (submission.pluginMeta() == null || !submission.pluginMeta().isObject()) {
            return new Submitted.Refused("That is not a debug report.");
        }
        return store(submission.pluginMeta(), submission.serverMeta(), sections(submission), 1);
    }

    private Submitted submitV2(JsonNode tree) {
        DebugSubmissionV2 submission;
        try {
            submission = json.treeToValue(tree, DebugSubmissionV2.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return new Submitted.Refused("The report does not have the shape of format 2.");
        }
        if (submission.plugin() == null || !submission.plugin().isObject()) {
            return new Submitted.Refused("The report names no plugin.");
        }
        if (text(submission.plugin(), "name", null) == null) {
            return new Submitted.Refused("The plugin has no name.");
        }
        List<DebugSubmissionV2.Section> uploaded = nonNull(submission.sections());
        if (uploaded.size() > MAX_SECTIONS) {
            return new Submitted.Refused(
                    "A report holds at most %d sections, this one has %d.".formatted(MAX_SECTIONS, uploaded.size()));
        }
        List<NewSection> sections = new ArrayList<>();
        Map<String, Integer> skipped = new TreeMap<>();
        for (int position = 0; position < uploaded.size(); position++) {
            var section = uploaded.get(position);
            if (section == null) return new Submitted.Refused("Section %d is empty.".formatted(position));
            if (section.kind() == null || section.kind().isBlank()) {
                return new Submitted.Refused(
                        "Section %d has no kind; known are %s.".formatted(position, List.of(DebugEntryKind.values())));
            }
            Optional<DebugEntryKind> kind = kind(section.kind());
            if (kind.isEmpty()) {
                skipped.merge(section.kind(), 1, Integer::sum);
                continue;
            }
            if (kind.get() == DebugEntryKind.CHART) {
                Optional<String> problem = chartProblem(section.chart());
                if (problem.isPresent()) {
                    return new Submitted.Refused("Section %d %s".formatted(position, problem.get()));
                }
                String type = section.chart().get("type").asText();
                if (ChartType.of(type).isEmpty()) {
                    skipped.merge("CHART " + type, 1, Integer::sum);
                    continue;
                }
            }
            String name = name(section.name(), kind.get().name());
            if (name.length() > MAX_NAME) {
                return new Submitted.Refused(
                        "The name of section %d is longer than %d characters.".formatted(position, MAX_NAME));
            }
            Optional<String> problem = displayProblem(kind.get(), section);
            if (problem.isPresent()) {
                return new Submitted.Refused("Section %d %s".formatted(position, problem.get()));
            }
            sections.add(new NewSection(kind.get(), name, content(section.content()), display(kind.get(), section)));
        }
        if (!skipped.isEmpty()) sections.add(skippedNote(skipped));
        return store(submission.plugin(), submission.server(), sections, 2);
    }

    /**
     * A metadata section naming the kinds that were left out and how many sections each had.
     */
    private static NewSection skippedNote(Map<String, Integer> skipped) {
        var entries = skipped.entrySet().stream()
                .map(entry -> new DebugReport.Entry(entry.getKey(), "%d section(s)".formatted(entry.getValue())))
                .toList();
        return new NewSection(
                DebugEntryKind.META,
                "Skipped sections",
                "This viewer does not know these kinds of sections, so they are not shown.",
                new Display(null, null, entries, null, null, null, null));
    }

    /**
     * @return what is wrong with how the section asks to be shown, as the end of a sentence naming it
     */
    private Optional<String> displayProblem(DebugEntryKind kind, DebugSubmissionV2.Section section) {
        if (section.format() != null && !Display.FORMATS.contains(section.format())) {
            return Optional.of("has the unknown format %s; known are %s.".formatted(section.format(), Display.FORMATS));
        }
        if (kind == DebugEntryKind.CHECK && !Display.LEVELS.contains(String.valueOf(section.level()))) {
            return Optional.of("is a check and needs a level, one of %s.".formatted(Display.LEVELS));
        }
        List<DebugReport.Entry> entries = nonNull(section.entries());
        if (entries.size() > MAX_ENTRIES) {
            return Optional.of("has more than %d entries.".formatted(MAX_ENTRIES));
        }
        for (var entry : entries) {
            if (entry == null
                    || entry.key() == null
                    || entry.key().isBlank()
                    || entry.key().length() > MAX_NAME) {
                return Optional.of(
                        "has an entry without a key, or with one longer than %d characters.".formatted(MAX_NAME));
            }
        }
        if (section.count() != null && section.count() < 1) {
            return Optional.of("occurred %d times; a count is at least 1.".formatted(section.count()));
        }
        if (tooLong(section.firstSeen()) || tooLong(section.lastSeen())) {
            return Optional.of("has a time longer than %d characters.".formatted(MAX_TIME));
        }
        return Optional.empty();
    }

    /**
     * Checks a chart's limits. The shape of its data is not checked: the viewer interprets whatever it gets.
     */
    private Optional<String> chartProblem(JsonNode chart) {
        if (chart == null || !chart.isObject()) {
            return Optional.of("is a chart and needs a chart object.");
        }
        JsonNode type = chart.get("type");
        if (type == null
                || !type.isTextual()
                || type.asText().isBlank()
                || type.asText().length() > MAX_CHART_TYPE) {
            return Optional.of("is a chart and needs a type of at most %d characters.".formatted(MAX_CHART_TYPE));
        }
        if (!chart.has("data") || chart.get("data").isNull()) {
            return Optional.of("is a chart and needs data.");
        }
        if (bytes(chart) > MAX_CHART_BYTES) {
            return Optional.of("has a chart larger than %d bytes.".formatted(MAX_CHART_BYTES));
        }
        if (depth(chart) > MAX_CHART_DEPTH) {
            return Optional.of("has a chart nested deeper than %d levels.".formatted(MAX_CHART_DEPTH));
        }
        if (values(chart) > MAX_CHART_VALUES) {
            return Optional.of("has a chart with more than %d values.".formatted(MAX_CHART_VALUES));
        }
        return Optional.empty();
    }

    private int bytes(JsonNode node) {
        try {
            return json.writeValueAsBytes(node).length;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A parsed chart could not be written back as JSON", e);
        }
    }

    private static int depth(JsonNode node) {
        if (!node.isContainerNode()) return 0;
        int deepest = 0;
        for (JsonNode child : node) deepest = Math.max(deepest, depth(child));
        return deepest + 1;
    }

    private static int values(JsonNode node) {
        if (!node.isContainerNode()) return 1;
        int count = 0;
        for (JsonNode child : node) count += values(child);
        return count;
    }

    /**
     * Keeps of the display hints what fits the kind: a level on a check, entries on metadata, counts and times on
     * exceptions, the chart on a chart.
     */
    private static Display display(DebugEntryKind kind, DebugSubmissionV2.Section section) {
        boolean exception = kind == DebugEntryKind.INTERNAL_EXCEPTION || kind == DebugEntryKind.EXTERNAL_EXCEPTION;
        List<DebugReport.Entry> entries = kind == DebugEntryKind.META && section.entries() != null
                ? section.entries().stream()
                        .map(entry -> new DebugReport.Entry(entry.key(), content(entry.value())))
                        .toList()
                : null;
        return new Display(
                section.format(),
                kind == DebugEntryKind.CHECK ? section.level() : null,
                entries,
                exception ? section.count() : null,
                exception ? section.firstSeen() : null,
                exception ? section.lastSeen() : null,
                kind == DebugEntryKind.CHART ? section.chart() : null);
    }

    private static boolean tooLong(String time) {
        return time != null && time.length() > MAX_TIME;
    }

    private Submitted store(JsonNode pluginMeta, JsonNode serverMeta, List<NewSection> sections, int format) {
        Keys keys = new Keys(newKey(), newKey());
        try {
            reports.create(
                    keys.hash(),
                    hash(keys.deletionHash()),
                    text(pluginMeta, "name", "Unknown plugin"),
                    text(pluginMeta, "version", "unknown"),
                    json.writeValueAsString(pluginMeta),
                    json.writeValueAsString(Objects.requireNonNullElse(serverMeta, json.createObjectNode())),
                    json.writeValueAsString(sections.stream().map(this::stored).toList()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A parsed report could not be written back as JSON", e);
        }
        return new Submitted.Stored(keys, Instant.now().plus(retention()), format);
    }

    private ObjectNode stored(NewSection section) {
        ObjectNode node = json.createObjectNode()
                .put("kind", section.kind().name())
                .put("name", section.name())
                .put("content", section.content());
        Display display = section.display();
        if (display.format() != null) node.put("format", display.format());
        if (display.level() != null) node.put("level", display.level());
        if (display.entries() != null) node.set("entries", json.valueToTree(display.entries()));
        if (display.occurrences() != null) node.put("occurrences", display.occurrences());
        if (display.firstSeen() != null) node.put("firstSeen", display.firstSeen());
        if (display.lastSeen() != null) node.put("lastSeen", display.lastSeen());
        if (display.chart() != null) node.set("chart", display.chart());
        return node;
    }

    public Optional<DebugReport> read(String readKey) {
        return reports.byReadKey(readKey)
                .map(head -> new DebugReport(
                        head.pluginName(),
                        head.pluginVersion(),
                        head.created(),
                        head.created().plus(retention()),
                        head.pluginMeta(),
                        head.serverMeta(),
                        reports.sections(head.id())));
    }

    public Optional<String> section(String readKey, int position) {
        return reports.content(readKey, position);
    }

    /**
     * @return whether a report was deleted
     */
    public boolean delete(String deleteKey) {
        return reports.deleteByDeleteKeyHash(hash(deleteKey));
    }

    /**
     * @return how many reports had expired
     */
    public int expire() {
        return reports.deleteOlderThanDays(settings.retentionDays());
    }

    public Duration retention() {
        return Duration.ofDays(settings.retentionDays());
    }

    /**
     * The sections of a report in the order the viewer shows them: what the plugin says about itself,
     * the logs, the exceptions, then its configuration.
     */
    static List<NewSection> sections(DebugSubmission submission) {
        List<NewSection> sections = new ArrayList<>();
        for (var entry : nonNull(submission.additionalPluginMeta())) {
            sections.add(new NewSection(DebugEntryKind.META, name(entry.name(), "Metadata"), content(entry.content())));
        }
        var log = submission.latestLog();
        if (log != null) {
            if (log.log() != null) sections.add(new NewSection(DebugEntryKind.LOG, "latest.log", log.log()));
            if (log.pluginLog() != null) {
                sections.add(new NewSection(DebugEntryKind.PLUGIN_LOG, "Plugin log", log.pluginLog()));
            }
            addExceptions(sections, DebugEntryKind.INTERNAL_EXCEPTION, "Plugin exception", log.internalExceptions());
            addExceptions(sections, DebugEntryKind.EXTERNAL_EXCEPTION, "Other exception", log.externalExceptions());
        }
        for (var entry : nonNull(submission.configDumps())) {
            sections.add(new NewSection(DebugEntryKind.CONFIG, name(entry.name(), "Config"), content(entry.content())));
        }
        return sections;
    }

    private static void addExceptions(
            List<NewSection> sections, DebugEntryKind kind, String name, List<String> exceptions) {
        int number = 1;
        for (String exception : nonNull(exceptions)) {
            if (exception == null || exception.isBlank()) continue;
            sections.add(new NewSection(kind, "%s %d".formatted(name, number++), exception));
        }
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String name(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name;
    }

    private static String content(String content) {
        return content == null ? "" : content;
    }

    private static Optional<DebugEntryKind> kind(String kind) {
        if (kind == null) return Optional.empty();
        for (DebugEntryKind known : DebugEntryKind.values()) {
            if (known.name().equals(kind)) return Optional.of(known);
        }
        return Optional.empty();
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank() ? fallback : value.asText();
    }

    private static JsonNode withoutNul(JsonNode node) {
        if (node == null) return null;
        if (node.isTextual()) return TextNode.valueOf(node.asText().replace("\u0000", ""));
        if (node instanceof ObjectNode object) {
            object.properties().forEach(field -> field.setValue(withoutNul(field.getValue())));
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) array.set(i, withoutNul(array.get(i)));
        }
        return node;
    }

    private static String newKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    static String hash(String key) {
        return Hashing.sha256().hashString(key, StandardCharsets.UTF_8).toString();
    }
}
