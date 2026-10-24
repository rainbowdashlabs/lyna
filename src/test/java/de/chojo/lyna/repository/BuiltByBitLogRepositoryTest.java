/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitCall;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitLogEntry;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitOutcome;
import de.chojo.lyna.feature.builtbybit.repository.BuiltByBitLogRepository;
import de.chojo.lyna.feature.builtbybit.repository.BuiltByBitLogRepository.Received;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The record of every call BuiltByBit made, as an operator reads it.
 */
class BuiltByBitLogRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 3401L;
    private static final long OTHER_GUILD = 3402L;

    private final BuiltByBitLogRepository calls = new BuiltByBitLogRepository();
    private int product;
    private int foreign;

    @BeforeEach
    void seed() throws SQLException {
        clear("builtbybit_purchase_log", "builtbybit_product", "product");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            product = insert(statement, GUILD, "Ours");
            foreign = insert(statement, OTHER_GUILD, "Theirs");
        }
    }

    private int insert(Statement statement, long guild, String name) throws SQLException {
        String sql = "INSERT INTO product (guild_id, name, role, free) VALUES (%d, '%s', 1, FALSE) RETURNING id"
                .formatted(guild, name);
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test
    @DisplayName("A recorded call reads back with everything it carried")
    void recordedCallReadsBack() {
        calls.record(new Received(BuiltByBitCall.PURCHASE, 5L, 1234L, 0L, product, "{}"), BuiltByBitOutcome.ISSUED);

        BuiltByBitLogEntry entry = calls.forGuild(GUILD, 10).getFirst();

        assertEquals(BuiltByBitCall.PURCHASE, entry.call());
        assertEquals(5L, entry.memberId());
        assertEquals(1234L, entry.resourceId());
        assertEquals(0L, entry.addonId());
        assertEquals(product, entry.productId());
        assertEquals("Ours", entry.productName());
        assertEquals("ISSUED", entry.outcome());
        assertEquals("{}", entry.raw());
    }

    @Test
    @DisplayName("A call nothing could be read from keeps only its body")
    void unreadableCallKeepsItsBody() {
        calls.record(new Received(BuiltByBitCall.KEY, null, null, null, null, "garbage"), BuiltByBitOutcome.INVALID);

        BuiltByBitLogEntry entry = calls.forGuild(GUILD, 10).getFirst();

        assertNull(entry.memberId());
        assertNull(entry.resourceId());
        assertNull(entry.productId());
        assertNull(entry.productName());
        assertEquals("garbage", entry.raw());
    }

    @Test
    @DisplayName("A guild sees its own calls and the unmatched ones, newest first, but not another guild's")
    void guildSeesItsOwnAndUnmatched() {
        calls.record(new Received(BuiltByBitCall.PURCHASE, 1L, 1L, 0L, product, "a"), BuiltByBitOutcome.ISSUED);
        calls.record(new Received(BuiltByBitCall.PURCHASE, 2L, 2L, 0L, foreign, "b"), BuiltByBitOutcome.ISSUED);
        calls.record(new Received(BuiltByBitCall.PURCHASE, 3L, 3L, 0L, null, "c"), BuiltByBitOutcome.UNKNOWN_PRODUCT);

        List<String> seen =
                calls.forGuild(GUILD, 10).stream().map(BuiltByBitLogEntry::raw).toList();

        assertEquals(List.of("c", "a"), seen);
    }

    @Test
    @DisplayName("The list stops at the limit")
    void limitHolds() {
        for (int i = 0; i < 5; i++) {
            calls.record(new Received(BuiltByBitCall.KEY, 1L, 1L, 0L, product, "x" + i), BuiltByBitOutcome.ISSUED);
        }

        assertEquals(3, calls.forGuild(GUILD, 3).size());
    }

    @Test
    @DisplayName("A deleted product leaves its calls behind, unmatched")
    void deletedProductLeavesCalls() throws SQLException {
        calls.record(new Received(BuiltByBitCall.PURCHASE, 1L, 1L, 0L, product, "a"), BuiltByBitOutcome.ISSUED);

        execute("DELETE FROM %s.product WHERE id = " + product);

        assertNull(calls.forGuild(GUILD, 10).getFirst().productId());
    }
}
