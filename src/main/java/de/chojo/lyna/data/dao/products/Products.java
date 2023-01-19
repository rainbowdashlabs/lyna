package de.chojo.lyna.data.dao.products;

import de.chojo.lyna.data.dao.LicenseGuild;
import net.dv8tion.jda.api.entities.Role;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Products {
    private final LicenseGuild guild;

    public Products(LicenseGuild guild) {
        this.guild = guild;
    }

    public Optional<Product> create(String name, Role role, @Nullable String url) {
        return builder(Product.class)
                .query("INSERT INTO product(guild_id, name, url, role) VALUES (?,?,?,?) RETURNING id")
                .parameter(stmt -> stmt.setLong(guild.guildId()).setString(name).setString(url).setLong(role.getIdLong()))
                .readRow(row -> new Product(this, row.getInt("id"), name, url, role.getIdLong()))
                .firstSync();
    }

    public List<Product> all() {
        return builder(Product.class)
                .query("SELECT id, name, url, role FROM product WHERE guild_id = ?")
                .parameter(stmt -> stmt.setLong(guild.guildId()))
                .readRow(row -> new Product(this, row.getInt("id"), row.getString("name"), row.getString("url"), row.getLong("role")))
                .allSync();
    }

    public Optional<Product> byId(int id) {
        return builder(Product.class)
                .query("SELECT id, name, url, role FROM product WHERE guild_id = ? and id = ?")
                .parameter(stmt -> stmt.setLong(guild.guildId()).setInt(id))
                .readRow(row -> new Product(this, row.getInt("id"), row.getString("name"), row.getString("url"), row.getLong("role")))
                .firstSync();
    }

    public long guildId() {
        return guild.guildId();
    }
}
