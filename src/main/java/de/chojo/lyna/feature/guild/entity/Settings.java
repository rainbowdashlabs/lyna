/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.guild.entity;

import de.chojo.lyna.feature.guild.LicenseGuild;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Settings {
    private final LicenseGuild licenseGuild;

    private LicenseSettings license = null;
    private TrialSettings trial;

    public Settings(LicenseGuild licenseGuild) {
        this.licenseGuild = licenseGuild;
    }

    public LicenseGuild licenseGuild() {
        return licenseGuild;
    }

    public long guildId() {
        return licenseGuild.guildId();
    }

    public LicenseSettings license() {
        if (license == null) {
            license = query("SELECT * FROM license_settings WHERE guild_id = ?")
                    .single(call().bind(guildId()))
                    .map(row -> new LicenseSettings(
                            this,
                            row.getInt("shares"),
                            row.getObject("admin_role_id") == null ? null : row.getLong("admin_role_id")))
                    .first()
                    .orElseGet(() -> new LicenseSettings(this));
        }
        return license;
    }

    public TrialSettings trial() {
        if (trial == null) {
            trial = query("SELECT * FROM trial_settings WHERE guild_id = ?")
                    .single(call().bind(guildId()))
                    .map(row -> new TrialSettings(this, row.getInt("server_time"), row.getInt("account_time")))
                    .first()
                    .orElseGet(() -> new TrialSettings(this));
        }
        return trial;
    }
}
