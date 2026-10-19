/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.mail.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * A guild's mail links as they are stored: a JSON array of {@code {label, url}}.
 */
public final class MailLinks {
    private static final TypeReference<List<MailLink>> TYPE = new TypeReference<>() {};
    private static final ObjectMapper JSON = new ObjectMapper();

    private MailLinks() {}

    /**
     * @param stored the stored array, or null for none
     */
    public static List<MailLink> read(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        try {
            return JSON.readValue(stored, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The stored mail links are not a list of links", e);
        }
    }

    /**
     * @param links the links, or null for none
     */
    public static String write(List<MailLink> links) {
        try {
            return JSON.writeValueAsString(links == null ? List.of() : links);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The mail links could not be written", e);
        }
    }
}
