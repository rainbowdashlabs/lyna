package de.chojo.lyna.data.dao.licenses;

import de.chojo.jdautil.util.Choice;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.platforms.Platform;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.util.LicenseCreator;
import de.chojo.sadu.wrapper.util.Row;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Licenses {
    private final LicenseGuild licenseGuild;

    public Licenses(LicenseGuild licenseGuild) {
        this.licenseGuild = licenseGuild;
    }

    public Optional<License> create(long seed, Product product, Platform platform, String identifier) {
        String key = LicenseCreator.create(seed, product, platform, identifier);
        return builder(License.class)
                .query("INSERT INTO license(product_id, platform_id, user_identifier, key) VALUES(?,?,?,?) ON CONFLICT DO NOTHING RETURNING id")
                .parameter(stmt -> stmt.setInt(product.id()).setInt(platform.id()).setString(identifier).setString(key))
                .readRow(row -> new License(product, platform, identifier, row.getInt("id"), key))
                .firstSync();
    }

    public Optional<License> byKey(String key) {
        return builder(License.class)
                .query("""
                       SELECT product_id, id, platform_id, user_identifier, key
                       FROM guild_license
                       WHERE key = ? AND guild_id = ?
                       """)
                .parameter(stmt -> stmt.setString(key).setLong(licenseGuild.guildId()))
                .readRow(this::buildLicense)
                .firstSync();
    }

    public Optional<License> byId(int id) {
        return builder(License.class)
                .query("""
                       SELECT product_id, id, platform_id, user_identifier, key
                       FROM guild_license
                       WHERE id = ? AND guild_id = ?
                       """)
                .parameter(stmt -> stmt.setInt(id).setLong(licenseGuild.guildId()))
                .readRow(this::buildLicense)
                .firstSync();
    }

    public Collection<Command.Choice> completeIdentifier(String value) {
        return builder(Command.Choice.class)
                .query("SELECT user_identifier FROM guild_license WHERE user_identifier ILIKE (? || '%') AND guild_id = ? LIMIT 25")
                .parameter(stmt -> stmt.setString(value).setLong(guildId()))
                .readRow(row -> Choice.toChoice(row.getString("user_identifier")))
                .allSync();
    }

    private long guildId() {
        return licenseGuild.guildId();
    }

    public Optional<License> byDetails(Product product, Platform platform, String identifier) {
        return builder(License.class)
                .query("SELECT * FROM guild_license WHERE product_id = ? AND platform_id = ? AND user_identifier = ? AND guild_id = ?")
                .parameter(stmt -> stmt.setInt(product.id()).setInt(platform.id()).setString(identifier).setLong(guildId()))
                .readRow(this::buildLicense)
                .firstSync();
    }

    public License buildLicense(Row row) throws SQLException {
        var product = licenseGuild.products().byId(row.getInt("product_id"));
        var platform = licenseGuild.platforms().byId(row.getInt("platform_id"));
        int id = row.getInt("id");
        return new License(product.get(), platform.get(), row.getString("user_identifier"), id, row.getString("key"));
    }
}
