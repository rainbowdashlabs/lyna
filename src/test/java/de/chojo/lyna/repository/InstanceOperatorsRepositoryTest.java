/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.instance.entity.InstanceOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceOperatorsRepositoryTest extends RepositoryTestBase {
    private static final long GRANTER = 100L;
    private static final long GRANTEE = 200L;

    @BeforeEach
    void clearOperators() throws SQLException {
        clear("instance_operator");
    }

    @Test
    @DisplayName("A granted id administers the instance, and one nobody granted does not")
    void addAndCheck() {
        assertFalse(instanceOperators.contains(GRANTEE));

        assertTrue(instanceOperators.add(GRANTEE, GRANTER));

        assertTrue(instanceOperators.contains(GRANTEE));
        assertFalse(instanceOperators.contains(999L));
    }

    @Test
    @DisplayName("Who granted it, and when, is kept")
    void grantIsRecorded() {
        instanceOperators.add(GRANTEE, GRANTER);

        InstanceOperator operator = instanceOperators.all().getFirst();
        assertEquals(GRANTEE, operator.discordId());
        assertEquals(GRANTER, operator.addedBy());
        assertNotNull(operator.addedAt());
    }

    @Test
    @DisplayName("A grant with nobody named to have made it is still a grant")
    void grantWithoutAGranter() {
        instanceOperators.add(GRANTEE, null);

        assertNull(instanceOperators.all().getFirst().addedBy());
        assertTrue(instanceOperators.contains(GRANTEE));
    }

    @Test
    @DisplayName("Granting the same id twice grants it once")
    void addIsIdempotent() {
        assertTrue(instanceOperators.add(GRANTEE, GRANTER));

        assertFalse(instanceOperators.add(GRANTEE, GRANTER));

        assertEquals(1, instanceOperators.count());
    }

    @Test
    @DisplayName("Withdrawing takes the instance back")
    void remove() {
        instanceOperators.add(GRANTEE, GRANTER);

        assertTrue(instanceOperators.remove(GRANTEE));

        assertFalse(instanceOperators.contains(GRANTEE));
        assertFalse(instanceOperators.remove(GRANTEE));
    }

    @Test
    @DisplayName("They are listed in the order they were granted it")
    void listedOldestFirst() {
        instanceOperators.add(GRANTEE, GRANTER);
        instanceOperators.add(201L, GRANTER);
        instanceOperators.add(202L, GRANTER);

        assertEquals(
                List.of(GRANTEE, 201L, 202L),
                instanceOperators.all().stream()
                        .map(InstanceOperator::discordId)
                        .toList());
        assertEquals(3, instanceOperators.count());
    }
}
