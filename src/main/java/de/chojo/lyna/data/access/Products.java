package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.products.Product;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Products {
    private final Guilds guilds;

    public Products(Guilds guilds) {
        this.guilds = guilds;
    }

    public Optional<Product> byId(int id) {
        return query("""
                SELECT id, guild_id, name, url, role, free FROM product WHERE id = ?
                """)
                .single(call().bind(id))
                .map(this::map)
                .first();
    }

    private Product map(Row row) throws SQLException {
        return guilds.guild(row.getLong("guild_id")).products().byId(row.getInt("id")).orElse(null);
    }
}
