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
import de.chojo.lyna.feature.debug.entity.DebugEntryKind;
import de.chojo.lyna.feature.debug.entity.DebugReport;
import de.chojo.lyna.feature.debug.entity.DebugReport.NewSection;
import de.chojo.lyna.feature.debug.entity.DebugSubmission;
import de.chojo.lyna.feature.debug.repository.DebugReportRepository;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Takes debug reports from plugins and hands them to whoever holds the link.
 *
 * <p>Keys are 32 random bytes. Whoever has the read key may read the report, so it must not be
 * guessable; UpdateButler derived its keys from the clock. The delete key is kept only as a hash,
 * which is enough to recognise it and useless to anyone reading the database.
 *
 * <p>PostgreSQL text cannot hold a NUL character, and logs sometimes do, so those are dropped.
 */
@Singleton
public class DebugReportService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

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
     * @return the keys of the stored report, or nothing when the body is not a report
     */
    public Optional<Keys> submit(String body) {
        DebugSubmission submission;
        try {
            submission = json.treeToValue(withoutNul(json.readTree(body)), DebugSubmission.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Optional.empty();
        }
        if (submission == null || submission.pluginMeta() == null || !submission.pluginMeta().isObject()) {
            return Optional.empty();
        }
        Keys keys = new Keys(newKey(), newKey());
        try {
            reports.create(
                    keys.hash(),
                    hash(keys.deletionHash()),
                    text(submission.pluginMeta(), "name", "Unknown plugin"),
                    text(submission.pluginMeta(), "version", "unknown"),
                    json.writeValueAsString(submission.pluginMeta()),
                    json.writeValueAsString(Objects.requireNonNullElse(
                            submission.serverMeta(), json.createObjectNode())),
                    json.writeValueAsString(sections(submission).stream()
                            .map(section -> Map.of(
                                    "kind", section.kind().name(),
                                    "name", section.name(),
                                    "content", section.content()))
                            .toList()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A parsed report could not be written back as JSON", e);
        }
        return Optional.of(keys);
    }

    public Optional<DebugReport> read(String readKey) {
        return reports.byReadKey(readKey).map(head -> new DebugReport(
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

    private static void addExceptions(List<NewSection> sections, DebugEntryKind kind, String name, List<String> exceptions) {
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
