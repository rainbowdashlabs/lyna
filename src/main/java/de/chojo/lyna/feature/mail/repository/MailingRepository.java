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
}
