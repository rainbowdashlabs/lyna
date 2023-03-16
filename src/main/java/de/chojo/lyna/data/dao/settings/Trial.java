package de.chojo.lyna.data.dao.settings;

import de.chojo.sadu.exceptions.ThrowingConsumer;
import de.chojo.sadu.wrapper.util.ParamBuilder;

import java.sql.SQLException;
import java.time.Duration;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Trial {
    private final Settings settings;
    private int serverTime;
    private int accountTime;

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
        if (set("server_time", stmt -> stmt.setLong(serverTime.toMinutes()))) {
            this.serverTime = (int) serverTime.toMinutes();
        }
    }

    public void accountTime(Duration accountTime) {
        if (set("account_time", stmt -> stmt.setLong(accountTime.toMinutes()))) {
            this.accountTime = (int) accountTime.toMinutes();
        }
    }

    private boolean set(String column, ThrowingConsumer<ParamBuilder, SQLException> consumer) {
        return builder().query("""
                        INSERT
                        INTO
                        	trial_settings(guild_id, %s)
                        VALUES
                        	(?, ?)
                        ON CONFLICT(guild_id) DO UPDATE SET
                        	%s = ?""", column)
                .parameter(stmt -> {
                    stmt.setLong(settings.guildId());
                    consumer.accept(stmt);
                }).update()
                .sendSync()
                .changed();
    }

}
