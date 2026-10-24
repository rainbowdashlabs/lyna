/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.google.inject.Inject;
import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.configuration.elements.BuiltByBit;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitLogEntry;
import de.chojo.lyna.feature.builtbybit.repository.BuiltByBitLogRepository;
import io.javalin.http.Context;

import java.util.List;

import static io.javalin.apibuilder.ApiBuilder.get;

/**
 * What BuiltByBit has asked of Lyna, and what to paste into BuiltByBit to set it up.
 *
 * <p>The secrets are instance configuration and are not shown; the addresses name where each goes.
 */
public class AdminBuiltByBit {
    static final int LOG_LIMIT = 200;
    static final String WEBHOOK_SECRET_PLACEHOLDER = "<BUILTBYBIT_WEBHOOKSECRET>";
    static final String PURCHASE_TEMPLATE = "{ \"username\": \"{username}\", \"user_id\": \"{user_id}\","
            + " \"user_url\": \"{user_url}\", \"resource_title\": \"{resource_title}\","
            + " \"resource_id\": \"{resource_id}\", \"resource_url\": \"{resource_url}\","
            + " \"addon_id\": \"{addon_id}\", \"addon_title\": \"{addon_title}\", \"bundle_id\": \"{bundle_id}\","
            + " \"bundle_title\": \"{bundle_title}\", \"renewal\": \"{renewal}\", \"list_price\": \"{list_price}\","
            + " \"final_price\": \"{final_price}\", \"purchase_date\": \"{purchase_date}\" }";

    private final GuildAdminGuard guard;
    private final BuiltByBitLogRepository calls;
    private final BuiltByBit config;
    private final Api api;

    /**
     * @param webhookUrl       where the purchase webhook goes, with a placeholder for the secret
     * @param purchaseTemplate the body the purchase webhook has to send
     * @param keyUrl           where the licence key placeholder goes
     * @param apiEnabled       whether BuiltByBit is asked before anything is issued
     * @param calls            the newest calls, newest first
     */
    public record BuiltByBitView(
            String webhookUrl, String purchaseTemplate, String keyUrl, boolean apiEnabled, List<CallView> calls) {}

    /**
     * @param memberId   as text, since a BuiltByBit id may outgrow what a JavaScript number holds exactly
     * @param resourceId as text, for the same reason
     * @param addonId    as text, or null for the resource itself
     */
    public record CallView(
            int id,
            String received,
            String call,
            String memberId,
            String resourceId,
            String addonId,
            Integer productId,
            String productName,
            String outcome,
            String raw) {}

    @Inject
    public AdminBuiltByBit(GuildAdminGuard guard, BuiltByBitLogRepository calls, BuiltByBit config, Api api) {
        this.guard = guard;
        this.calls = calls;
        this.config = config;
        this.api = api;
    }

    /**
     * Mounts the routes under the guild being administered.
     */
    public void init() {
        get("builtbybit", this::view);
    }

    private void view(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        String base = api.url() + "/api/v1/builtbybit/";
        ctx.json(new BuiltByBitView(
                base + WEBHOOK_SECRET_PLACEHOLDER,
                PURCHASE_TEMPLATE,
                base + "key",
                config.apiEnabled(),
                calls.forGuild(admin.guild().guildId(), LOG_LIMIT).stream()
                        .map(AdminBuiltByBit::view)
                        .toList()));
    }

    private static CallView view(BuiltByBitLogEntry entry) {
        return new CallView(
                entry.id(),
                entry.received().toString(),
                entry.call().name(),
                text(entry.memberId()),
                text(entry.resourceId()),
                entry.addonId() == null || entry.addonId() == 0
                        ? null
                        : entry.addonId().toString(),
                entry.productId(),
                entry.productName(),
                entry.outcome(),
                entry.raw());
    }

    private static String text(Long id) {
        return id == null ? null : id.toString();
    }
}
