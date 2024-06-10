package de.chojo.lyna.data.dao.settings;

import de.chojo.sadu.queries.api.call.Call;

import java.time.Duration;
import java.util.function.Function;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Trial {
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
        return query("""
                INSERT
                INTO
                	trial_settings(guild_id, %s)
                VALUES
                	(?, ?)
                ON CONFLICT(guild_id) DO UPDATE SET
                	%s = ?""", column)
                .single(consumer.apply(call().bind(settings.guildId())))
                .update()
                .changed();
    }

}
