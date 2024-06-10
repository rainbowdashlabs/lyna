package de.chojo.lyna.data.dao.licenses;

import de.chojo.jdautil.util.Choice;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.util.LicenseCreator;
import de.chojo.sadu.mapper.wrapper.Row;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.slf4j.LoggerFactory.getLogger;

public class Licenses {
    private final LicenseGuild licenseGuild;
    private static final Logger log = getLogger(Licenses.class);

    public Licenses(LicenseGuild licenseGuild) {
        this.licenseGuild = licenseGuild;
    }

    public Optional<License> create(Product product, String identifier) {
        String key = LicenseCreator.create(licenseGuild.configuration().config().license().baseSeed(), product, identifier);
        log.info(LogNotify.STATUS, "Creating license key for {} purchased by {}", product.name(), identifier);
        return query("INSERT INTO license(product_id, user_identifier, key) VALUES(?,?,?) ON CONFLICT DO NOTHING RETURNING id")
                .single(call().bind(product.id()).bind(identifier).bind(key))
                .map(row -> new License(product, identifier, row.getInt("id"), key))
                .first()
                .or(() -> byKey(key));
    }

    public Optional<License> byKey(String key) {
        return query("""
                SELECT product_id, id, user_identifier, key
                FROM guild_license
                WHERE key = ? AND guild_id = ?
                """)
                .single(call().bind(key).bind(licenseGuild.guildId()))
                .map(this::buildLicense)
                .first();
    }

    public Optional<License> byId(int id) {
        return query("""
                SELECT product_id, id, user_identifier, key
                FROM guild_license
                WHERE id = ? AND guild_id = ?
                """)
                .single(call().bind(id).bind(licenseGuild.guildId()))
                .map(this::buildLicense)
                .first();
    }

    public Collection<Command.Choice> completeIdentifier(String value) {
        return query("SELECT user_identifier FROM guild_license WHERE user_identifier ILIKE (? || '%') AND guild_id = ? LIMIT 25")
                .single(call().bind(value).bind(guildId()))
                .map(row -> Choice.toChoice(row.getString("user_identifier")))
                .all();
    }

    private long guildId() {
        return licenseGuild.guildId();
    }

    public Optional<License> byDetails(Product product, String identifier) {
        return query("SELECT * FROM guild_license WHERE product_id = ? AND user_identifier = ? AND guild_id = ?")
                .single(call().bind(product.id()).bind(identifier).bind(guildId()))
                .map(this::buildLicense)
                .first();
    }

    public License buildLicense(Row row) throws SQLException {
        var product = licenseGuild.products().byId(row.getInt("product_id"));
        int id = row.getInt("id");
        return new License(product.get(), row.getString("user_identifier"), id, row.getString("key"));
    }
}
