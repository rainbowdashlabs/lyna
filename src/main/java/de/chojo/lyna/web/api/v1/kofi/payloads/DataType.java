package de.chojo.lyna.web.api.v1.kofi.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum DataType {
    @JsonProperty("Shop Order")
    SHOP_ORDER, SUBSCRIPTION, DONATION, COMMISSION
}
