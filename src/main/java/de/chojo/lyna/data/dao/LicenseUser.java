package de.chojo.lyna.data.dao;

import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

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
        return query("SELECT product_id, user_identifier, id, key FROM user_guild_license WHERE product_id = ? AND user_id = ?")
                .single(call().bind(product.id()).bind(id()))
                .map(row -> licenseGuild.licenses().buildLicense(row))
                .first();
    }

    public Optional<License> subLicenseByProduct(Product product) {
        return query("SELECT product_id, user_identifier, id, key FROM user_guild_sub_license WHERE product_id = ? AND user_id = ?")
                .single(call().bind(product.id()).bind(id()))
                .map(licenseGuild.licenses()::buildLicense)
                .first();
    }

    public List<License> licenses() {
        return query("""
                SELECT product_id, user_identifier, id, key
                FROM user_guild_license
                WHERE user_id = ? AND guild_id = ?
                """)
                .single(call().bind(id()))
                .map(licenseGuild.licenses()::buildLicense)
                .all();
    }

    public List<License> sharedLicenses() {
        return query("""
                SELECT product_id, user_identifier, id, key
                FROM user_guild_sub_license
                WHERE user_id = ? AND guild_id = ?
                """)
                .single(call().bind(id()))
                .map(licenseGuild.licenses()::buildLicense)
                .all();
    }

    public boolean canAccess(Product product) {
        return query("""
                 SELECT EXISTS(
                     SELECT product_id, user_identifier, id, key, user_id, guild_id
                     FROM user_guild_license
                     WHERE guild_id = ?
                         AND user_id = ?
                         AND product_id = ?)
                     OR EXISTS(
                         SELECT product_id, user_identifier, id, key, user_id, guild_id
                         FROM user_guild_sub_license
                         WHERE guild_id = ?
                             AND user_id = ?
                             AND product_id = ?) AS exists
                """).single(call().bind(guildId()).bind(id()).bind(product.id())
                        .bind(guildId()).bind(id()).bind(product.id()))
                .map(row -> row.getBoolean("exists"))
                .first()
                .orElse(false);
    }

    public List<Command.Choice> completeOwnProducts(String value) {
        return query("SELECT id, name FROM user_products WHERE guild_id = ? AND user_id = ? AND name ILIKE ('%' || ? || '%')")
                .single(call().bind(guildId()).bind(id()).bind(value))
                .map(row -> new Command.Choice(row.getString("name"), row.getInt("id")))
                .all();
    }

    public List<Command.Choice> completeDownloadableProducts(String value) {
        List<Command.Choice> byLicense = query("SELECT id, name FROM user_products WHERE guild_id = ? AND user_id = ? AND name ILIKE ('%' || ? || '%')")
                .single(call().bind(guildId()).bind(id()).bind(value))
                .map(row -> new Command.Choice(row.getString("name"), row.getInt("id")))
                .all();
        List<Command.Choice> byRole =
                query("SELECT product_id, name FROM role_access a LEFT JOIN product p ON a.product_id = p.id WHERE ARRAY[role_id] && ? AND name ILIKE ('%' || ? || '%')")
                        .single(call().bind(member.getRoles().stream().map(ISnowflake::getIdLong).toList(), PostgreSqlTypes.BIGINT).bind(value))
                        .map(row -> new Command.Choice(row.getString("name"), row.getInt("product_id")))
                        .all();
        List<Command.Choice> free = query("SELECT id, name FROM product WHERE free AND guild_id = ? AND name ILIKE ('%' || ? || '%')")
                .single(call().bind(guildId()).bind(value))
                .map(row -> new Command.Choice(row.getString("name"), row.getInt("id")))
                .all();

        var result = new HashSet<>(byLicense);
        result.addAll(byRole);
        result.addAll(free);
        return result.stream().limit(25).toList();
    }

    public List<Command.Choice> completeAllProducts(String value) {
        return query("SELECT id, name FROM user_products_all WHERE guild_id = ? AND user_id = ? AND name ILIKE ('%' || ? || '%')")
                .single(call().bind(guildId()).bind(id()).bind(value))
                .map(row -> new Command.Choice(row.getString("name"), row.getInt("id")))
                .all();
    }

    public long guildId() {
        return licenseGuild.guildId();
    }

    public Guild guild() {
        return licenseGuild.guild();
    }

    public List<Product> products() {
        return query("SELECT id FROM user_products_all WHERE user_id = ? AND guild_id = ?")
                .single(call().bind(id()).bind(guildId()))
                .map(row -> row.getInt("id"))
                .all()
                .stream()
                .map(id -> licenseGuild.products().byId(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
