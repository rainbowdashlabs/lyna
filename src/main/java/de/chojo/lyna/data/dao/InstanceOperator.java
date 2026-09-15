package de.chojo.lyna.data.dao;

import java.time.Instant;

/**
 * Somebody granted the whole instance through the web.
 *
 * @param addedBy who granted it, or nothing when the row predates the record being kept
 */
public record InstanceOperator(long discordId, Long addedBy, Instant addedAt) {
}
