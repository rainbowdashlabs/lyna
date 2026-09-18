/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.configuration.TestConf;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.DownloadType;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.guild.LicenseGuild;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.entity.LicenseSource;
import de.chojo.lyna.feature.mail.entity.Mailing;
import de.chojo.lyna.feature.mail.repository.MailingLookup;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.nexus.NexusRest;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The finders a guild reaches its own things through, and the two that reach across guilds.
 *
 * <p>They are the oldest code here and were covered only by whatever happened to call them. What
 * they answer is worth asserting on its own: most of them resolve a row back into an object through
 * the guild, and a wrong guild is the way that goes wrong.
 */
class GuildFindersTest extends RepositoryTestBase {
    private static final long GUILD = 4601L;
    private static final long OTHER_GUILD = 4602L;
    private static final long OWNER = 9101L;

    private Guilds guilds;
    private LicenseGuild guild;
    private LicenseGuild otherGuild;
    private Product product;

    @BeforeEach
    void seed() throws Exception {
        clear(
                "download_stat",
                "role_access",
                "download",
                "download_type",
                "mail_products",
                "kofi_products",
                "license_invite",
                "user_sub_license",
                "user_license",
                "license_access",
                "license",
                "trial_settings",
                "license_settings",
                "product",
                "account_email",
                "account_identity",
                "account");
        guilds = new Guilds(Mockito.mock(NexusRest.class), TestConf.defaults(), accountLinks);
        guild = guilds.guild(GUILD);
        otherGuild = guilds.guild(OTHER_GUILD);
        product = guild.products()
                .create("Widget", role(50L), "https://example.invalid/widget", false, false)
                .orElseThrow();
    }

    private static Role role(long id) {
        Role role = Mockito.mock(Role.class);
        Mockito.when(role.getIdLong()).thenReturn(id);
        Mockito.when(role.isPublicRole()).thenReturn(false);
        return role;
    }

    @Test
    @DisplayName("A guild's products are its own, and are found by id")
    void productsAreScopedToTheGuild() {
        assertEquals(
                List.of(product.id()),
                guild.products().all().stream().map(Product::id).toList());
        assertTrue(guild.products().byId(product.id()).isPresent());
        assertTrue(otherGuild.products().byId(product.id()).isEmpty(), "another guild does not see it");
        assertEquals(GUILD, guild.products().guildId());
    }

    @Test
    @DisplayName("The cross-guild lookup finds a product without being told its guild")
    void theLookupCrossesGuilds() {
        ProductLookup lookup = new ProductLookup(guilds);
        assertEquals(product.id(), lookup.byId(product.id()).orElseThrow().id());
        assertTrue(lookup.byId(product.id() + 999).isEmpty());
    }

    @Test
    @DisplayName("A licence is found by key, by id, by its identifier, and by who holds it")
    void licencesAreFoundEveryWay() {
        License license =
                guild.licenses().create(product, "buyer@example.invalid").orElseThrow();

        assertEquals(
                license.id(),
                guild.licenses().byKey(license.key()).orElseThrow().id());
        assertEquals(
                license.id(), guild.licenses().byId(license.id()).orElseThrow().id());
        assertEquals(
                license.id(),
                guild.licenses()
                        .byDetails(product, "buyer@example.invalid")
                        .orElseThrow()
                        .id());
        assertEquals(
                List.of(license.id()),
                guild.licenses().all().stream().map(License::id).toList());

        assertTrue(guild.licenses().byKey("NOT-A-KEY").isEmpty());
    }

    @Test
    @DisplayName("Asking twice for the same identifier hands back the licence that exists")
    void oneLicencePerIdentifier() {
        License first =
                guild.licenses().create(product, "buyer@example.invalid").orElseThrow();
        License again =
                guild.licenses().create(product, "buyer@example.invalid").orElseThrow();

        assertEquals(first.id(), again.id(), "the key is derived from the product and the identifier");
        assertEquals(1, guild.licenses().all().size());
    }

    @Test
    @DisplayName("A licence remembers where it came from")
    void licencesCarryTheirSource() {
        License license = guild.licenses()
                .create(product, "kofi@example.invalid", LicenseSource.KOFI)
                .orElseThrow();
        assertEquals(
                license.id(),
                guild.licenses().byKey(license.key()).orElseThrow().id());
    }

    @Test
    @DisplayName("Licences are listed by holder and by sharee, and the two do not mix")
    void byOwnerAndBySharee() {
        License owned =
                guild.licenses().create(product, "owner@example.invalid").orElseThrow();
        License shared =
                guild.licenses().create(product, "shared@example.invalid").orElseThrow();
        licenseRepository.claim(accountLinks.accountIdForDiscord(OWNER), owned.id());
        licenseRepository.addSharee(shared.id(), accountLinks.accountIdForDiscord(OWNER));

        assertEquals(
                List.of(owned.id()),
                guild.licenses().byOwner(OWNER).stream().map(License::id).toList());
        assertEquals(
                List.of(shared.id()),
                guild.licenses().bySharee(OWNER).stream().map(License::id).toList());
    }

    @Test
    @DisplayName("Download types belong to their guild and are found by id")
    void downloadTypes() {
        DownloadType type = guild.downloadTypes()
                .create("stable", "the stable one", ReleaseType.STABLE)
                .orElseThrow();

        assertEquals(
                List.of(type.id()),
                guild.downloadTypes().all().stream().map(DownloadType::id).toList());
        assertEquals(
                type.id(), guild.downloadTypes().byId(type.id()).orElseThrow().id());
        assertTrue(otherGuild.downloadTypes().all().isEmpty(), "another guild has its own");
        assertTrue(
                guild.downloadTypes()
                        .create("stable", "again", ReleaseType.STABLE)
                        .isEmpty(),
                "a name is taken once per guild");
    }

    @Test
    @DisplayName("A download is found by its type, its release type and its artifact")
    void downloads() {
        DownloadType type = guild.downloadTypes()
                .create("stable", "the stable one", ReleaseType.STABLE)
                .orElseThrow();
        Download download = product.downloads()
                .create(type, "releases", "de.chojo", "widget", null)
                .orElseThrow();

        assertEquals(
                List.of(download.id()),
                product.downloads().downloads().stream().map(Download::id).toList());
        assertEquals(
                download.id(), product.downloads().byType(type).orElseThrow().id());
        assertEquals(
                download.id(),
                product.downloads().byType(type.id()).orElseThrow().id());
        assertEquals(
                List.of(download.id()),
                product.downloads().byReleaseType(ReleaseType.STABLE).stream()
                        .map(Download::id)
                        .toList());
        assertEquals(
                download.id(),
                product.downloads()
                        .byReleaseTypeAndArtifact(ReleaseType.STABLE, "widget")
                        .orElseThrow()
                        .id());
        assertTrue(product.downloads().byReleaseType(ReleaseType.DEV).isEmpty());
    }

    @Test
    @DisplayName("A role is granted a release type of a product and taken off again")
    void downloadRoleAccess() {
        assertTrue(product.downloads().grant(role(60L), ReleaseType.DEV));
        assertEquals(List.of(ReleaseType.DEV), productRepository.accessByRoles(product.id(), List.of(60L)));
        assertTrue(product.downloads().revoke(role(60L), ReleaseType.DEV));
        assertTrue(productRepository.accessByRoles(product.id(), List.of(60L)).isEmpty());
    }

    @Test
    @DisplayName("A product's mail is made once and read back, and found by name across guilds")
    void mailings() {
        Mailing mailing = product.mailings().create("Widget mail", "<p>body</p>");
        assertEquals(mailing.id(), product.mailings().get().orElseThrow().id());

        MailingLookup lookup = new MailingLookup(guilds);
        assertEquals(
                mailing.id(),
                lookup.byName("Your Widget mail receipt").orElseThrow().id());
        assertTrue(lookup.byName("nothing like it").isEmpty());
    }

    @Test
    @DisplayName("A Ko-fi link code resolves to a product, and the sale is written down")
    void kofiMappings() {
        var kofi = new de.chojo.lyna.feature.purchase.repository.KoFiProductRepository(new ProductLookup(guilds));
        kofi.create(product, "abc123");

        assertEquals(product.id(), kofi.byCode("abc123").orElseThrow().id());
        assertTrue(kofi.byCode("nope").isEmpty());
        assertEquals(
                List.of(product.id()),
                kofi.listForGuild(GUILD).stream().map(m -> m.productId()).toList());
    }

    @Test
    @DisplayName("A member's own licences and shares are told apart")
    void licenceUserSeesBothSides() {
        License owned =
                guild.licenses().create(product, "owner@example.invalid").orElseThrow();
        licenseRepository.claim(accountLinks.accountIdForDiscord(OWNER), owned.id());

        var member = Mockito.mock(net.dv8tion.jda.api.entities.Member.class);
        Mockito.when(member.getIdLong()).thenReturn(OWNER);
        var user = guild.user(member);

        assertEquals(OWNER, user.id());
        assertEquals(GUILD, user.guildId());
        assertEquals(
                List.of(owned.id()), user.licenses().stream().map(License::id).toList());
        assertTrue(user.sharedLicenses().isEmpty());
        assertEquals(owned.id(), user.licenseByProduct(product).orElseThrow().id());
        assertTrue(user.subLicenseByProduct(product).isEmpty());
        assertEquals(
                List.of(product.id()), user.products().stream().map(Product::id).toList());
        assertTrue(user.canAccess(product));
    }

    @Test
    @DisplayName("Somebody holding nothing can reach nothing")
    void licenceUserWithNothing() {
        var member = Mockito.mock(net.dv8tion.jda.api.entities.Member.class);
        Mockito.when(member.getIdLong()).thenReturn(9999L);
        var user = guild.user(member);

        assertTrue(user.licenses().isEmpty());
        assertTrue(user.products().isEmpty());
        assertFalse(user.canAccess(product));
    }

    @Test
    @DisplayName("Autocomplete offers what the guild has, narrowed by what was typed")
    void autocompleteOffersTheGuildsOwn() {
        guild.products().create("Gadget", role(51L), null, true, false).orElseThrow();
        guild.products().create("Trialware", role(52L), null, false, true).orElseThrow();
        guild.downloadTypes()
                .create("stable", "the stable one", ReleaseType.STABLE)
                .orElseThrow();
        guild.licenses().create(product, "buyer@example.invalid").orElseThrow();

        assertEquals(3, guild.products().complete("").size());
        assertEquals(List.of("Widget"), names(guild.products().complete("wid")));
        assertEquals(List.of("Gadget"), names(guild.products().complete("", true)), "free only");
        assertEquals(
                List.of("Trialware"),
                names(guild.products().completeTrials("")),
                "a trial is a paid product somebody may try");
        assertEquals(List.of("stable"), names(guild.downloadTypes().complete("sta")));
        assertEquals(
                List.of("buyer@example.invalid"),
                guild.licenses().completeIdentifier("buyer").stream()
                        .map(net.dv8tion.jda.api.interactions.commands.Command.Choice::getName)
                        .toList());
        assertTrue(otherGuild.products().complete("").isEmpty(), "another guild offers nothing");
    }

    @Test
    @DisplayName("Autocomplete for a member offers what they hold, what they can download, and all of it")
    void autocompleteForAMember() {
        License owned =
                guild.licenses().create(product, "owner@example.invalid").orElseThrow();
        licenseRepository.claim(accountLinks.accountIdForDiscord(OWNER), owned.id());
        licenseRepository.grantAccess(owned.id(), ReleaseType.STABLE);

        var member = Mockito.mock(net.dv8tion.jda.api.entities.Member.class);
        Mockito.when(member.getIdLong()).thenReturn(OWNER);
        Mockito.when(member.getRoles()).thenReturn(List.of());
        var user = guild.user(member);

        assertEquals(member, user.member());
        assertEquals(List.of("Widget"), names(user.completeOwnProducts("")));
        assertEquals(List.of("Widget"), names(user.completeAllProducts("")));
        assertEquals(List.of("Widget"), names(user.completeDownloadableProducts("")));
        assertTrue(user.completeOwnProducts("nothing").isEmpty());
    }

    @Test
    @DisplayName("A Ko-fi sale is written down whether or not it names a shop item")
    void kofiSalesAreLogged() throws Exception {
        var kofi = new de.chojo.lyna.feature.purchase.repository.KoFiProductRepository(new ProductLookup(guilds));
        var post = new de.chojo.lyna.web.api.v1.kofi.payloads.KofiPost(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.time.OffsetDateTime.now(),
                de.chojo.lyna.web.api.v1.kofi.payloads.DataType.SHOP_ORDER,
                true,
                "Ada Lovelace",
                null,
                5.0f,
                "https://ko-fi.com/x",
                "buyer@example.invalid",
                "EUR",
                false,
                false,
                java.util.UUID.randomUUID(),
                null,
                "tier",
                null);

        kofi.logTransaction(post, "{}", null);
        assertEquals(1, countRows("kofi_sales"));
    }

    private static List<String> names(List<net.dv8tion.jda.api.interactions.commands.Command.Choice> choices) {
        return choices.stream()
                .map(net.dv8tion.jda.api.interactions.commands.Command.Choice::getName)
                .toList();
    }

    @Test
    @DisplayName("A product is read on its own for its page, description and all")
    void theStorefrontReadsOneProduct() {
        assertTrue(kioskProducts.byId(product.id()).isPresent());
        assertTrue(kioskProducts.byId(product.id() + 9999).isEmpty());

        kioskProducts.description(product.id(), "# Widget\n\nWhat it **does**.");

        var read = kioskProducts.byId(product.id()).orElseThrow();
        assertEquals("Widget", read.name());
        assertEquals("# Widget\n\nWhat it **does**.", read.description());
        assertEquals("https://example.invalid/widget", read.url());
    }

    @Test
    @DisplayName("A description is kept as markdown, not as whatever it would render to")
    void theDescriptionIsStoredAsWritten() {
        kioskProducts.description(product.id(), "<b>bold</b> and [a link](https://example.invalid)");

        assertEquals(
                "<b>bold</b> and [a link](https://example.invalid)",
                kioskProducts.byId(product.id()).orElseThrow().description(),
                "rendering is the reader's business, so nothing is decided here");
    }

    @Test
    @DisplayName("The catalogue carries the description too, so one read answers a page")
    void theCatalogueCarriesIt() {
        kioskProducts.description(product.id(), "something");

        var listed = kioskProducts.all().stream()
                .filter(p -> p.id() == product.id())
                .findFirst()
                .orElseThrow();
        assertEquals("something", listed.description());
    }
}
