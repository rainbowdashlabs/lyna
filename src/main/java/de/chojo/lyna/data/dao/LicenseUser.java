package de.chojo.lyna.data.dao;

import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.List;
import java.util.Optional;

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

    public List<Command.Choice> completeProducts(String value) {
        return builder(Command.Choice.class)
                .query("SELECT id, name FROM user_products WHERE guild_id = ? AND user_id = ? AND name ILIKE (? || '%')")
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
}
