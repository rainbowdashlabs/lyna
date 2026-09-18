/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.dao.settings;

import de.chojo.lyna.feature.guild.repository.GuildSettingsRepository;
import de.chojo.sadu.queries.api.call.Call;

import java.time.Duration;
import java.util.function.Function;

public class Trial {
    private static final GuildSettingsRepository REPOSITORY = new GuildSettingsRepository();

    private final Settings settings;
    private int serverTime = 30;
    private int accountTime = 43200;

    public Trial(Settings settings) {
        this.settings = settings;
    }

    public Trial(Settings settings, int serverTime, int accountTime) {
        this.settings = settings;
        this.serverTime = serverTime;
        this.accountTime = accountTime;
    }

    public Duration serverTime() {
        return Duration.ofMinutes(serverTime);
    }

    public Duration accountTime() {
        return Duration.ofMinutes(accountTime);
    }

    public void serverTime(Duration serverTime) {
        if (set("server_time", stmt -> stmt.bind(serverTime.toMinutes()))) {
            this.serverTime = (int) serverTime.toMinutes();
        }
    }

    public void accountTime(Duration accountTime) {
        if (set("account_time", stmt -> stmt.bind(accountTime.toMinutes()))) {
            this.accountTime = (int) accountTime.toMinutes();
        }
    }

    private boolean set(String column, Function<Call, Call> consumer) {
        return REPOSITORY.setTrial(column, c -> consumer.apply(c.bind(settings.guildId())));
    }
}
