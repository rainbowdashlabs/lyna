/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.mail.repository;

import com.google.inject.Singleton;
import de.chojo.sadu.queries.api.call.Call;

import java.util.function.Function;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The per-product mail an operator composes.
 */
@Singleton
public class MailingRepository {

    /**
     * Writes one column of a mail template.
     *
     * <p>The column is interpolated because a placeholder cannot name one. Every caller passes a
     * literal, and nothing here takes a column name from outside.
     */
    public boolean set(int mailingId, String column, Function<Call, Call> value) {
        return query("""
                UPDATE
                    mail_products
                SET %s = ?
                WHERE
                    id = ?""", column)
                .single(value.apply(call()).bind(mailingId))
                .update()
                .changed();
    }

    /**
     * Writes the block document the mail is composed from.
     */
    public boolean setBlocks(int mailingId, String blocks) {
        return query("UPDATE mail_products SET blocks = ?::JSONB WHERE id = ?")
                .single(call().bind(blocks).bind(mailingId))
                .update()
                .changed();
    }

    /**
     * Empties a mail, so that its product sends its guild's default again.
     */
    public boolean clearBody(int mailingId) {
        return query("UPDATE mail_products SET blocks = NULL, mail_text = '' WHERE id = ?")
                .single(call().bind(mailingId))
                .update()
                .changed();
    }

    /**
     * Gives every product of a guild that has no mail an empty one, named after the product, so that
     * each sends the guild's default and can be matched against a receipt by its name.
     *
     * @return how many mails were made
     */
    public int createMissing(long guildId) {
        return query("""
                INSERT INTO mail_products (product_id, name, mail_text)
                SELECT p.id, p.name, ''
                FROM product p
                WHERE p.guild_id = ?
                  AND NOT EXISTS (SELECT 1 FROM mail_products m WHERE m.product_id = p.id)
                """).single(call().bind(guildId)).insert().rows();
    }
}
