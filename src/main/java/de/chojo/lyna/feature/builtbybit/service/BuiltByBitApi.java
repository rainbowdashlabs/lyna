/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.service;

import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitListing;

/**
 * Asks BuiltByBit whether a member really holds a licence for a listing.
 *
 * <p>The API needs BuiltByBit Ultimate. Without a token this is {@link #NONE}, which never has an
 * opinion, and the shared secrets are all that stands behind a call.
 */
public interface BuiltByBitApi {
    /** No API configured. */
    BuiltByBitApi NONE = (memberId, listing) -> Verdict.UNCHECKED;

    /**
     * @return whether BuiltByBit vouches for the member holding a licence for the listing
     */
    Verdict confirm(long memberId, BuiltByBitListing listing);

    /**
     * What BuiltByBit said about a licence.
     */
    enum Verdict {
        /** The member holds a licence that is in force. */
        CONFIRMED,
        /** The member holds no licence in force. */
        DENIED,
        /** No API is configured, so nobody was asked. */
        UNCHECKED,
        /** The API is configured but did not answer. */
        UNAVAILABLE
    }
}
