/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.guild.entity;

import de.chojo.lyna.feature.guild.repository.GuildSettingsRepository;

public class LicenseSettings {
    private static final GuildSettingsRepository REPOSITORY = new GuildSettingsRepository();

    private final Settings settings;
    int shares = 0;
    Long adminRoleId;

    public LicenseSettings(Settings settings) {
        this.settings = settings;
    }

    public LicenseSettings(Settings settings, int shares, Long adminRoleId) {
        this.settings = settings;
        this.shares = shares;
        this.adminRoleId = adminRoleId;
    }

    public int shares() {
        return shares;
    }

    public void shares(int shares) {
        if (REPOSITORY.setShares(guildId(), shares)) {
            this.shares = shares;
        }
    }

    /**
     * The Discord role whose members administer this guild here, beyond those who already may by
     * holding MANAGE_SERVER.
     *
     * <p>How a guild delegates its admin area without handing out a server-wide permission.
     *
     * @return the role, or nothing when the guild has named none
     */
    public Long adminRoleId() {
        return adminRoleId;
    }

    public void adminRoleId(Long adminRoleId) {
        if (REPOSITORY.setAdminRole(guildId(), adminRoleId)) {
            this.adminRoleId = adminRoleId;
        }
    }

    public long guildId() {
        return settings.guildId();
    }
}
