package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.products.KioskProduct;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The public catalogue: every product of every guild, merged.
 *
 * <p>Guild ownership is an administrative concern, not a shopping one, so a visitor browsing the
 * storefront never picks a guild. Nothing here touches the gateway, which is what lets the page
 * answer to somebody who is not signed in.
 */
public class KioskProducts {
    /** Ko-fi serves a shop item at this address followed by its direct link code. */
    private static final String KOFI_SHOP_URL = "https://ko-fi.com/s/";

    /**
     * @return every product, free and premium alike, by name
     */
    public List<KioskProduct> all() {
        return query("""
                SELECT p.id, p.guild_id, p.name, p.url, p.icon_url, p.free, kp.link_code
                FROM product p
                LEFT JOIN kofi_products kp ON kp.product_id = p.id
                ORDER BY p.name
                """)
                .single()
                .map(row -> new KioskProduct(
                        row.getInt("id"),
                        row.getLong("guild_id"),
                        row.getString("name"),
                        row.getString("url"),
                        row.getString("icon_url"),
                        row.getBoolean("free"),
                        row.getString("link_code") == null ? null : KOFI_SHOP_URL + row.getString("link_code")))
                .all();
    }

    /**
     * Records where a product's icon is hosted. A blank address takes the icon away again, which is
     * what leaves the tile on its generated monogram.
     */
    public void iconUrl(int productId, String iconUrl) {
        query("UPDATE product SET icon_url = ? WHERE id = ?")
                .single(call().bind(iconUrl == null || iconUrl.isBlank() ? null : iconUrl.trim()).bind(productId))
                .update();
    }
}
