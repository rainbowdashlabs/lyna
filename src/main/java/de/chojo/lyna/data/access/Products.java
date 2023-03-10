package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Products {
    private ShardManager shardManager;
    private final Guilds guilds;

    public Products(Guilds guilds) {
        this.guilds = guilds;
    }

    public Optional<Product> byId(int id) {
        return builder(Product.class)
                .query("""
                        SELECT id, guild_id, name, url, role, free FROM product WHERE id = ?
                        """)
                .parameter(stmt -> stmt.setInt(id))
                .readRow(row -> {
                    Guild guild = shardManager.getGuildById(row.getLong("guild_id"));
                    return guilds.guild(guild).products().byId(id).orElse(null);
                }).firstSync();
    }

    public void shardManager(ShardManager shardManager) {
        this.shardManager = shardManager;
    }
}
