package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;

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
                .map(row -> {
                    Guild guild = shardManager.getGuildById(row.getLong("guild_id"));
                    return guilds.guild(guild).products().byId(id).orElse(null);
                }).first();
    }

    public void shardManager(ShardManager shardManager) {
        this.shardManager = shardManager;
    }
}
