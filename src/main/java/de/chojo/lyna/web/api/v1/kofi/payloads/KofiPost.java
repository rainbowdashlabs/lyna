package de.chojo.lyna.web.api.v1.kofi.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record KofiPost(@JsonProperty("verification_token") UUID verificationToken,
                       @JsonProperty("message_id") UUID messageId,
                       @JsonProperty("timestamp") OffsetDateTime timestamp,
                       @JsonProperty("type") DataType type,
                       @JsonProperty("is_public") boolean isPublic,
                       @JsonProperty("from_name") String from,
                       @JsonProperty("message") @Nullable String message,
                       @JsonProperty("amount") Float amount,
                       @JsonProperty("url") String url,
                       @JsonProperty("email") String email,
                       @JsonProperty("currency") String currency,
                       @JsonProperty("is_subscription_payment") boolean isSubscriptionPayment,
                       @JsonProperty("is_first_subscription_payment") boolean isFirstSubscriptionPayment,
                       @JsonProperty("kofi_transaction_id") UUID kofiTransactionId,
                       @JsonProperty("shop_items") @Nullable List<ShopItem> shopItems,
                       @JsonProperty("tier_name") String tierName,
                       @JsonProperty("shipping") Shipping shipping

                       ) {

}
