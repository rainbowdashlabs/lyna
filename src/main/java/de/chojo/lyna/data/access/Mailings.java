/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.feature.mail.entity.Mailing;
import de.chojo.lyna.feature.product.entity.Product;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Mailings {
    private final Guilds guilds;

    public Mailings(Guilds guilds) {
        this.guilds = guilds;
    }

    public Optional<Mailing> byName(String name) {
        return query("""
                SELECT mp.id, guild_id, product_id, mp.name, mail_text, mp.blocks
                FROM mail_products mp
                         LEFT JOIN product p ON mp.product_id = p.id
                WHERE ? ILIKE ('%' || mp.name || '%')
                """)
                .single(call().bind(name))
                .map(row -> {
                    LicenseGuild licenseGuild = guilds.guild(row.getLong("guild_id"));
                    Product product = licenseGuild
                            .products()
                            .byId(row.getInt("product_id"))
                            .get();
                    return new Mailing(
                            row.getInt("id"),
                            product,
                            row.getString("name"),
                            row.getString("mail_text"),
                            row.getString("blocks"));
                })
                .first();
    }
}
