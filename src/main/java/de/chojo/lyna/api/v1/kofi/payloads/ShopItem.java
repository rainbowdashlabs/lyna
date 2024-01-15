package de.chojo.lyna.api.v1.kofi.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ShopItem(@JsonProperty("direct_link_code") String directLinkCode,
                       @JsonProperty("variation_name") String variationName,
                       @JsonProperty("quantity") int quantity) {
}
