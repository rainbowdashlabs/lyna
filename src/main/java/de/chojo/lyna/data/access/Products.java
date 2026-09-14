package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.products.Product;
import de.chojo.sadu.mapper.wrapper.Row;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Products {
    private ShardManager shardManager;
    private final Guilds guilds;

    public Products(Guilds guilds) {
        this.guilds = guilds;
    }

    public Optional<Product> byId(int id) {
        return query("""
                SELECT id, guild_id, name, url, role, free FROM product WHERE id = ?
                """)
                .single(call().bind(id))
                .map(this::map)
                .first();
    }

    public List<Product> freeProducts() {
        return query("""
                SELECT id, guild_id, name, url, role, free FROM product WHERE free
                """)
                .single()
                .map(this::map)
                .all();
    }

    public void shardManager(ShardManager shardManager) {
        this.shardManager = shardManager;
    }

    /**
     * Resolves a row through the guild that owns it, which is where its download types and Nexus
     * client live.
     *
     * <p>That makes this path need the gateway. A deployment running without the bot can still
     * serve the storefront, which reads the tables directly, but not the downloads behind it - and
     * saying so is more use than the null pointer it used to throw.
     */
    private Product map(Row row) throws SQLException {
        if (shardManager == null) {
            throw new GatewayUnavailableException("Downloads need the Discord bot, which is switched off here");
        }
        Guild guild = shardManager.getGuildById(row.getLong("guild_id"));
        return guilds.guild(guild).products().byId(row.getInt("id")).orElse(null);
    }
}
