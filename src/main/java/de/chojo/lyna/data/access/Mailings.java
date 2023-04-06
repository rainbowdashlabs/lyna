package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.platforms.Platform;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.dao.products.mailings.Mailing;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Mailings {
    private ShardManager shardManager;
    private final Guilds guilds;

    public Mailings(Guilds guilds) {
        this.guilds = guilds;
    }

    public Optional<Mailing> byName(String name) {
        return builder(Mailing.class)
                .query("""
                        SELECT mp.id, guild_id, platform_id, product_id, mp.name, mail_text
                        FROM mail_products mp
                                 LEFT JOIN product p ON mp.product_id = p.id
                        WHERE ? ILIKE ('%' || mp.name || '%')
                        """)
                .parameter(stmt -> stmt.setString(name))
                .readRow(row -> {
                    Guild guild = shardManager.getGuildById(row.getLong("guild_id"));
                    LicenseGuild licenseGuild = guilds.guild(guild);
                    Product product = licenseGuild.products().byId(row.getInt("product_id")).get();
                    Platform platform = licenseGuild.platforms().byId(row.getInt("product_id")).get();
                    return new Mailing(row.getInt("id"), product, platform, row.getString("name"), row.getString("mail_text"));
                }).firstSync();
    }

    public void shardManager(ShardManager shardManager) {
        this.shardManager = shardManager;
    }
}
