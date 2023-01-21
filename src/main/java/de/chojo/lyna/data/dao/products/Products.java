package de.chojo.lyna.data.dao.products;

import de.chojo.lyna.data.dao.LicenseGuild;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Products {
    private final LicenseGuild licenseGuild;

    public Products(LicenseGuild licenseGuild) {
        this.licenseGuild = licenseGuild;
    }

    public Optional<Product> create(String name, Role role, @Nullable String url) {
        return builder(Product.class)
                .query("INSERT INTO product(guild_id, name, url, role) VALUES (?,?,?,?) RETURNING id")
                .parameter(stmt -> stmt.setLong(licenseGuild.guildId()).setString(name).setString(url)
                                       .setLong(role.getIdLong()))
                .readRow(row -> new Product(this, row.getInt("id"), name, url, role.getIdLong()))
                .firstSync();
    }

    public List<Product> all() {
        return builder(Product.class)
                .query("SELECT id, name, url, role FROM product WHERE guild_id = ?")
                .parameter(stmt -> stmt.setLong(licenseGuild.guildId()))
                .readRow(row -> new Product(this, row.getInt("id"), row.getString("name"), row.getString("url"), row.getLong("role")))
                .allSync();
    }

    public List<Command.Choice> complete(String value) {
        return all().stream().filter(p -> p.name().toLowerCase().startsWith(value) || value.isBlank())
                    .map(p -> new Command.Choice(p.name(), p.id()))
                    .limit(25)
                    .toList();

    }

    public Optional<Product> byId(int id) {
        return builder(Product.class)
                .query("SELECT id, name, url, role FROM product WHERE guild_id = ? AND id = ?")
                .parameter(stmt -> stmt.setLong(licenseGuild.guildId()).setInt(id))
                .readRow(row -> new Product(this, row.getInt("id"), row.getString("name"), row.getString("url"), row.getLong("role")))
                .firstSync();
    }

    public long guildId() {
        return licenseGuild.guildId();
    }

    public Guild guild() {
        return licenseGuild.guild();
    }

    public LicenseGuild licenseGuild() {
        return licenseGuild;
    }
}
