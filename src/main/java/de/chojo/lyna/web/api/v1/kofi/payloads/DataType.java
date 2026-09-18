/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.kofi.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum DataType {
    @JsonProperty("Shop Order")
    SHOP_ORDER,
    SUBSCRIPTION,
    DONATION,
    COMMISSION
}
