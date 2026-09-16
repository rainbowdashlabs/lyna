package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.account.AccountLicense;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Licenses as the account area reads them: by account, across every guild at once.
 *
 * <p>Separate from {@link de.chojo.lyna.data.dao.licenses.Licenses}, which hangs off a guild and
 * speaks in JDA members. The web has neither: a visitor holds licenses in whichever guilds issued
 * them and never picks one, and the API answers the same whether or not the bot is connected.
 *
 * <p>Everything here is keyed by account, which is what a session already carries - so a holder who
 * has never touched Discord is no different from one who has.
 */
public class AccountLicenses {
    private static final String SELECT = """
            SELECT
                l.id,
                p.guild_id,
                p.id                                                      AS product_id,
                p.name                                                    AS product_name,
                p.url                                                     AS product_url,
                l.user_identifier,
                COALESCE(ARRAY(SELECT la.release_type::TEXT
                               FROM license_access la
                               WHERE la.license_id = l.id
                               ORDER BY la.release_type), ARRAY[]::TEXT[]) AS release_types,
                COALESCE(ul.account_id, 0)                                AS owner_id,
                (SELECT count(*) FROM user_sub_license s WHERE s.license_id = l.id) AS sharees_used,
                COALESCE(ls.shares, 0)                                    AS sharees_cap
            FROM license l
                JOIN product p ON p.id = l.product_id
                LEFT JOIN user_license ul ON ul.license_id = l.id
                LEFT JOIN license_settings ls ON ls.guild_id = p.guild_id
            """;

    /**
     * @param accountId the account asking
     * @return every license that account owns, by product name
     */
    public List<AccountLicense> owned(int accountId) {
        return query(SELECT + """
                WHERE ul.account_id = ?
                ORDER BY p.name
                """)
                .single(call().bind(accountId))
                .map(row -> read(row, AccountLicense.Role.OWNER))
                .all();
    }

    /**
     * @param accountId the account asking
     * @return every license shared with that account
     */
    public List<AccountLicense> shared(int accountId) {
        return query(SELECT + """
                WHERE EXISTS (SELECT 1 FROM user_sub_license s WHERE s.license_id = l.id AND s.account_id = ?)
                ORDER BY p.name
                """)
                .single(call().bind(accountId))
                .map(row -> read(row, AccountLicense.Role.SHAREE))
                .all();
    }

    /**
     * One license, as the id asking for it may see it.
     *
     * @param licenseId the license
     * @param accountId the account asking
     * @return the license when that account owns it or was shared it, nothing otherwise
     */
    public Optional<AccountLicense> forHolder(int licenseId, int accountId) {
        return query(SELECT + """
                WHERE l.id = ?
                  AND (ul.account_id = ?
                       OR EXISTS (SELECT 1 FROM user_sub_license s WHERE s.license_id = l.id AND s.account_id = ?))
                """)
                .single(call().bind(licenseId).bind(accountId).bind(accountId))
                .map(row -> read(row,
                        row.getLong("owner_id") == accountId
                                ? AccountLicense.Role.OWNER
                                : AccountLicense.Role.SHAREE))
                .first();
    }

    /**
     * @param licenseId the license
     * @return the accounts the license is shared with
     */
    public List<Integer> sharees(int licenseId) {
        return query("SELECT account_id FROM user_sub_license WHERE license_id = ? ORDER BY account_id")
                .single(call().bind(licenseId))
                .map(row -> row.getInt("account_id"))
                .all();
    }

    /**
     * Shares the license with one more account. Sharing with an account that already holds it changes
     * nothing, so a repeated request is not an error.
     *
     * @return whether a new sharee was added
     */
    public boolean addSharee(int licenseId, int accountId) {
        return query("""
                INSERT INTO user_sub_license (account_id, license_id)
                VALUES (?, ?)
                ON CONFLICT (account_id, license_id) DO NOTHING
                """)
                .single(call().bind(accountId).bind(licenseId))
                .insert()
                .changed();
    }

    /**
     * @return whether that account had been a sharee
     */
    public boolean removeSharee(int licenseId, int accountId) {
        return query("DELETE FROM user_sub_license WHERE license_id = ? AND account_id = ?")
                .single(call().bind(licenseId).bind(accountId))
                .delete()
                .changed();
    }

    /**
     * @param licenseId the license
     * @return the key, only for an account that holds the license
     */
    public Optional<String> keyForHolder(int licenseId, int accountId) {
        return forHolder(licenseId, accountId).flatMap(license -> query("SELECT key FROM license WHERE id = ?")
                .single(call().bind(licenseId))
                .map(row -> row.getString("key"))
                .first());
    }

    /**
     * The products an account may download, whether it owns the license or was shared one.
     *
     * <p>What the storefront needs to decide between a download button and a buy button, in one
     * query rather than one per tile.
     *
     * @param accountId the account asking
     * @return the product ids that account is entitled to
     */
    public Set<Integer> entitledProductIds(int accountId) {
        return Set.copyOf(query("""
                SELECT DISTINCT l.product_id
                FROM license l
                WHERE EXISTS (SELECT 1 FROM user_license u WHERE u.license_id = l.id AND u.account_id = ?)
                   OR EXISTS (SELECT 1 FROM user_sub_license s WHERE s.license_id = l.id AND s.account_id = ?)
                """)
                .single(call().bind(accountId).bind(accountId))
                .map(row -> row.getInt("product_id"))
                .all());
    }

    /**
     * The release types an account may download of one product.
     *
     * <p>Read from the licenses that account holds, which is the half of the bot's rule the web can
     * answer. The other half - access granted by a Discord role - needs the gateway to know which
     * roles somebody wears, so a web visitor sees what their licenses carry and nothing more.
     *
     * @param accountId the account asking
     * @param productId the product
     * @return the release types, as the names the RELEASE_TYPE enum uses
     */
    public Set<String> releaseTypes(int accountId, int productId) {
        return Set.copyOf(query("""
                SELECT DISTINCT a.release_type::TEXT AS release_type
                FROM (SELECT account_id, license_id FROM user_license
                      UNION
                      SELECT account_id, license_id FROM user_sub_license) h
                    JOIN license l ON l.id = h.license_id
                    JOIN license_access a ON a.license_id = h.license_id
                WHERE h.account_id = ? AND l.product_id = ? AND a.release_type IS NOT NULL
                """)
                .single(call().bind(accountId).bind(productId))
                .map(row -> row.getString("release_type"))
                .all());
    }

    private static AccountLicense read(Row row, AccountLicense.Role role) throws SQLException {
        return new AccountLicense(
                row.getInt("id"),
                row.getLong("guild_id"),
                row.getInt("product_id"),
                row.getString("product_name"),
                row.getString("product_url"),
                row.getString("user_identifier"),
                readStringArray(row.getArray("release_types")),
                role,
                row.getInt("owner_id"),
                row.getInt("sharees_used"),
                row.getInt("sharees_cap"));
    }

    private static List<String> readStringArray(Array array) throws SQLException {
        if (array == null) return List.of();
        Object raw = array.getArray();
        return raw instanceof String[] strings ? List.of(strings) : List.of();
    }
}
