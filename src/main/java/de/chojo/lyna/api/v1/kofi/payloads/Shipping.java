package de.chojo.lyna.api.v1.kofi.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Shipping(@JsonProperty("full_name")String fullName,
                       @JsonProperty("street_address")String streetAddress,
                       @JsonProperty("city")String city,
                       @JsonProperty("state_or_province")String stateOrProvince,
                       @JsonProperty("postal_code")String postalCode,
                       @JsonProperty("country")String country,
                       @JsonProperty("country_code")String countryCode,
                       @JsonProperty("telephone")String telephone) {
}
