/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.entity;

import java.time.Instant;

/**
 * An address an account holds.
 *
 * @param verifiedAt when a link sent to this address was followed, or null if that never happened.
 *                   Everything an address is good for - signing in, collecting a licence bought with
 *                   it - hangs off this being set. An unverified address is a claim.
 * @param primary    whether this is the one the application sends to
 */
public record AccountEmail(int accountId, String email, Instant verifiedAt, Instant addedAt, boolean primary) {
    public boolean verified() {
        return verifiedAt != null;
    }
}
