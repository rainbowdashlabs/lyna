/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.entity;

/**
 * What Lyna did about a call from BuiltByBit.
 */
public enum BuiltByBitOutcome {
    /** A licence exists for the buyer now, whether this call made it or an earlier one did. */
    ISSUED,
    /** The body is not what Lyna asked BuiltByBit to send. */
    INVALID,
    /** No product is sold as that resource or add-on. */
    UNKNOWN_PRODUCT,
    /** A bundle was bought. Bundles are not supported; an operator issues by hand. */
    BUNDLE,
    /** The API says the member holds no licence for it. */
    DENIED,
    /** The API is configured but did not answer, so nothing was issued. */
    API_UNAVAILABLE
}
