package de.chojo.lyna.web.api.v1.products;

import com.google.inject.Inject;
import de.chojo.lyna.data.access.AccountLicenses;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.access.KioskProducts;
import de.chojo.lyna.data.dao.account.AccountIdentity;
import de.chojo.lyna.data.dao.products.KioskProduct;
import de.chojo.lyna.web.api.auth.Auth;
import io.javalin.http.Context;

import java.util.List;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

/**
 * The storefront catalogue.
 *
 * <p>Every product, not only the free ones: a premium product a visitor cannot download is still
 * something they may want to buy, and a tile that is missing tells them nothing. What each visitor
 * may do with a product is answered per request from the licenses their Discord id holds.
 */
public class Products {
    private final KioskProducts kiosk;
    private final Auth auth;
    private final Accounts accounts;
    private final AccountLicenses licenses;

    @Inject
    public Products(KioskProducts kiosk, Auth auth, Accounts accounts, AccountLicenses licenses) {
        this.kiosk = kiosk;
        this.auth = auth;
        this.accounts = accounts;
        this.licenses = licenses;
    }

    public void init() {
        path("products", () -> get(this::list));
    }

    private void list(Context ctx) {
        Set<Integer> entitled = entitlements(ctx);
        List<KioskEntry> entries = kiosk.all().stream()
                .map(product -> KioskEntry.of(product, entitled.contains(product.id())))
                .toList();
        ctx.json(entries);
    }

    /**
     * What the caller may already download.
     *
     * <p>Anonymous, unlinked and unknown all mean the same thing here - nothing entitled - so none
     * of them is an error. A free product is downloadable regardless and is not listed.
     */
    private Set<Integer> entitlements(Context ctx) {
        return auth.currentSession(ctx)
                .map(session -> licenses.entitledProductIds(session.accountId()))
                .orElse(Set.of());
    }

    /**
     * @param entitled whether this visitor holds a license covering the product
     */
    private record KioskEntry(int id, String guildId, String name, String url, String iconUrl, boolean free,
                              String purchaseUrl, boolean entitled) {
        static KioskEntry of(KioskProduct product, boolean entitled) {
            return new KioskEntry(
                    product.id(),
                    Long.toString(product.guildId()),
                    product.name(),
                    product.url(),
                    product.iconUrl(),
                    product.free(),
                    product.purchaseUrl(),
                    entitled);
        }
    }
}
