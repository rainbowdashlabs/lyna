package de.chojo.lyna.data.dao;

import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.sadu.types.PostgreSqlTypes;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class LicenseUser {
    private final LicenseGuild licenseGuild;
    long id;
    /**
     * The member attached to this user
     */
    Member member;

    public LicenseUser(LicenseGuild licenseGuild, Member member) {
        this.licenseGuild = licenseGuild;
        this.member = member;
        id = member.getIdLong();
    }


    public long id() {
        return id;
    }

    public Member member() {
        return member;
    }

    public Optional<License> licenseByProduct(Product product) {
        return builder(License.class)
                .query("SELECT product_id, platform_id, user_identifier, id, key FROM user_guild_license WHERE product_id = ? AND user_id = ?")
                .parameter(stmt -> stmt.setInt(product.id()).setLong(id()))
                .readRow(licenseGuild.licenses()::buildLicense)
                .firstSync();
    }

    public Optional<License> subLicenseByProduct(Product product) {
        return builder(License.class)
                .query("SELECT product_id, platform_id, user_identifier, id, key FROM user_guild_sub_license WHERE product_id = ? AND user_id = ?")
                .parameter(stmt -> stmt.setInt(product.id()).setLong(id()))
                .readRow(licenseGuild.licenses()::buildLicense)
                .firstSync();
    }

    public List<License> licenses() {
        return builder(License.class)
                .query("""
                       SELECT product_id, platform_id, user_identifier, id, key
                       FROM user_guild_license
                       WHERE user_id = ? AND guild_id = ?
                       """)
                .parameter(stmt -> stmt.setLong(id()))
                .readRow(licenseGuild.licenses()::buildLicense)
                .allSync();
    }

    public List<License> sharedLicenses() {
        return builder(License.class)
                .query("""
                       SELECT product_id, platform_id, user_identifier, id, key
                       FROM user_guild_sub_license
                       WHERE user_id = ? AND guild_id = ?
                       """)
                .parameter(stmt -> stmt.setLong(id()))
                .readRow(licenseGuild.licenses()::buildLicense)
                .allSync();
    }

    public boolean canAccess(Product product) {
        return builder(Boolean.class)
                .query("""
                        SELECT EXISTS(
                            SELECT product_id, platform_id, user_identifier, id, key, user_id, guild_id
                            FROM user_guild_license
                            WHERE guild_id = ?
                                AND user_id = ?
                                AND product_id = ?)
                            OR EXISTS(
                                SELECT product_id, platform_id, user_identifier, id, key, user_id, guild_id
                                FROM user_guild_sub_license
                                WHERE guild_id = ?
                                    AND user_id = ?
                                    AND product_id = ?) AS exists
                       """).parameter(stmt -> stmt.setLong(guildId()).setLong(id()).setInt(product.id())
                                                  .setLong(guildId()).setLong(id()).setInt(product.id()))
                .readRow(row -> row.getBoolean("exists"))
                .firstSync()
                .orElse(false);
    }

    public List<Command.Choice> completeOwnProducts(String value) {
        return builder(Command.Choice.class)
                .query("SELECT id, name FROM user_products WHERE guild_id = ? AND user_id = ? AND name ILIKE (? || '%')")
                .parameter(stmt -> stmt.setLong(guildId()).setLong(id()).setString(value))
                .readRow(row -> new Command.Choice(row.getString("name"), row.getInt("id")))
                .allSync();
    }
    public Set<Command.Choice> completeDownloadableProducts(String value) {
        List<Command.Choice> byLicense = builder(Command.Choice.class)
                .query("SELECT id, name FROM user_products WHERE guild_id = ? AND user_id = ? AND name ILIKE (? || '%')")
                .parameter(stmt -> stmt.setLong(guildId()).setLong(id()).setString(value))
                .readRow(row -> new Command.Choice(row.getString("name"), row.getInt("id")))
                .allSync();
        List<Command.Choice> byRole = builder(Command.Choice.class)
                .query("SELECT product_id, name FROM role_access a LEFT JOIN product p ON a.product_id = p.id WHERE ARRAY[role_id] && ? ")
                .parameter(stmt -> stmt.setArray(member.getRoles().stream().map(ISnowflake::getIdLong).toList(), PostgreSqlTypes.BIGINT))
                .readRow(row -> new Command.Choice(row.getString("name"), row.getInt("product_id")))
                .allSync();
        var result = new HashSet<>(byLicense);
        result.addAll(byRole);
        return result;
    }

    public List<Command.Choice> completeAllProducts(String value) {
        return builder(Command.Choice.class)
                .query("SELECT id, name FROM user_products_all WHERE guild_id = ? AND user_id = ? AND name ILIKE (? || '%')")
                .parameter(stmt -> stmt.setLong(guildId()).setLong(id()).setString(value))
                .readRow(row -> new Command.Choice(row.getString("name"), row.getInt("id")))
                .allSync();
    }

    public List<Command.Choice> completePlatform(String value) {
        return builder(Command.Choice.class)
                .query("SELECT id, name FROM user_platforms WHERE guild_id = ? AND user_id = ? AND name ILIKE (? || '%')")
                .parameter(stmt -> stmt.setLong(guildId()).setLong(id()).setString(value))
                .readRow(row -> new Command.Choice(row.getString("name"), row.getInt("id")))
                .allSync();
    }

    public long guildId() {
        return licenseGuild.guildId();
    }

    public Guild guild() {
        return licenseGuild.guild();
    }

    public List<Product> products() {
        return builder(Integer.class)
                .query("SELECT id FROM user_products_all WHERE user_id = ? AND guild_id = ?")
                .parameter(stmt -> stmt.setLong(id()).setLong(guildId()))
                .readRow(row -> row.getInt("id"))
                .allSync()
                .stream()
                .map(id -> licenseGuild.products().byId(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
