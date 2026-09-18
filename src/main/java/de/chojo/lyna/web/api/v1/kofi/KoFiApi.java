/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.kofi;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.inject.Inject;
import de.chojo.lyna.configuration.elements.Kofi;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.access.KoFiProducts;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.licenses.LicenseSource;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.dao.products.mailings.Mailing;
import de.chojo.lyna.mail.MailCreator;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.mail.PurchaseRecipient;
import de.chojo.lyna.util.Urls;
import de.chojo.lyna.web.api.v1.kofi.payloads.DataType;
import de.chojo.lyna.web.api.v1.kofi.payloads.KofiPost;
import de.chojo.lyna.web.api.v1.kofi.payloads.ShopItem;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static org.slf4j.LoggerFactory.getLogger;

public class KoFiApi {
    private final Kofi kofiSettings;
    private static final Logger log = getLogger(KoFiApi.class);

    private final KoFiProducts kofi;
    private final MailingService mailing;
    private final Accounts accounts;
    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(JsonReadFeature.ALLOW_MISSING_VALUES, true)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .findAndAddModules()
            .build();

    @Inject
    public KoFiApi(Kofi kofiSettings, KoFiProducts kofi, MailingService mailing, Accounts accounts) {
        this.kofiSettings = kofiSettings;
        this.kofi = kofi;
        this.mailing = mailing;
        this.accounts = accounts;
    }

    public void init() {
        path("kofi", () -> {
            post(ctx -> {
                var results = Urls.splitQuery(ctx.body());
                var json = results.get("data");
                var post = mapper.readValue(json, KofiPost.class);
                var presented = post.verificationToken();
                if (presented == null || !presented.toString().equals(kofiSettings.verificationToken())) {
                    ctx.status(HttpStatus.FORBIDDEN);
                    return;
                }
                log.info("Received new purchase from kofi: {}", json);
                if (post.type() == DataType.SHOP_ORDER) {
                    for (ShopItem shopItem : post.shopItems()) {
                        kofi.logTransaction(post, json, shopItem);
                        Optional<Product> optProduct = kofi.byCode(shopItem.directLinkCode());
                        if (optProduct.isEmpty()) continue;
                        Product product = optProduct.get();
                        Optional<Mailing> optProductMail = product.mailings().get();
                        if (optProductMail.isEmpty()) continue;
                        Mailing productMail = optProductMail.get();
                        Optional<License> license = product.createLicense(post.email(), LicenseSource.KOFI);
                        if (license.isEmpty()) continue;
                        license.get().grantAccess(ReleaseType.STABLE);
                        boolean handedOver = accounts.handOver(license.get().id(), post.email());
                        var mail = MailCreator.createLicenseMessage(
                                mailing.renderer(),
                                productMail,
                                license.get().key(),
                                post.from(),
                                post.email(),
                                product.url(),
                                handedOver ? PurchaseRecipient.WITH_ACCOUNT : PurchaseRecipient.WITHOUT_ACCOUNT);
                        mailing.sendMail(mail);
                    }
                } else {
                    kofi.logTransaction(post, json, null);
                }
                ctx.status(HttpStatus.OK);
            });
        });
    }
}
