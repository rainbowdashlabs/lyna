package de.chojo.lyna.web.api.v1.kofi;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import de.chojo.lyna.data.access.KoFiProducts;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.dao.products.mailings.Mailing;
import de.chojo.lyna.mail.MailCreator;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.util.Urls;
import de.chojo.lyna.web.api.v1.V1;
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
    private final V1 v1;
    private static final Logger log = getLogger(KoFiApi.class);

    private final KoFiProducts kofi;
    private final MailingService mailing;
    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(JsonReadFeature.ALLOW_MISSING_VALUES, true)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .findAndAddModules()
            .build();

    public KoFiApi(V1 v1, KoFiProducts kofi, MailingService mailing) {
        this.v1 = v1;
        this.kofi = kofi;
        this.mailing = mailing;
    }

    public void init() {
        path("kofi", () -> {
            post(ctx -> {
                var results = Urls.splitQuery(ctx.body());
                var json = results.get("data");
                var post = mapper.readValue(json, KofiPost.class);
                if (!post.verificationToken().equals(v1.configuration().config().kofi().verificationToken())) {
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
                        Optional<License> license = product.createLicense("kofi:%s".formatted(post.email()));
                        if (license.isEmpty()) continue;
                        license.get().grantAccess(ReleaseType.STABLE);
                        var mail = MailCreator.createLicenseMessage(productMail, license.get().key(), post.from(), post.email());
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
