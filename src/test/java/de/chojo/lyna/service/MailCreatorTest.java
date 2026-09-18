package de.chojo.lyna.service;

import de.chojo.lyna.data.dao.products.Products;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.dao.products.mailings.Mailing;
import de.chojo.lyna.mail.Mail;
import de.chojo.lyna.mail.MailCreator;
import de.chojo.lyna.mail.MailTemplateRenderer;
import de.chojo.lyna.mail.PurchaseRecipient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a licence mail says about the account it belongs to.
 *
 * <p>The section beneath the operator's own text makes a claim about who holds the licence, so each
 * variant has to appear only where the application actually knows it to be true.
 */
class MailCreatorTest {

    private static final String BASE = "https://lyna.example.invalid";

    private final MailTemplateRenderer renderer = new MailTemplateRenderer("Workbench", BASE);

    private Mail render(String address, PurchaseRecipient recipient) {
        Products products = Mockito.mock(Products.class);
        Product product = new Product(products, 1, "Widget", "https://example.invalid/widget", 0L, false, false);
        Mailing mailing = new Mailing(1, product, "widget mail", "<p>Thanks for buying {{ product }}.</p><p>{{ key }}</p>", null);
        return MailCreator.createLicenseMessage(renderer, mailing, "LYNA-TEST-KEY-0001",
                "Ada Lovelace", address, product.url(), recipient);
    }

    @Test
    @DisplayName("Somebody with no account here is invited to sign up with the address they bought with")
    void withoutAccountInvites() {
        String body = render("buyer@example.invalid", PurchaseRecipient.WITHOUT_ACCOUNT).text();

        assertTrue(body.contains(BASE + "/signup?email=buyer%40example.invalid"),
                "the signup link carries the address they paid from");
        assertTrue(body.contains("buyer@example.invalid"), "and it is written out so they can read it");
        assertFalse(body.contains("/account/licenses"),
                "they have no account, so nothing points at one");
    }

    @Test
    @DisplayName("Somebody whose account already holds it is pointed at the account, not at the key")
    void withAccountPointsAtIt() {
        String body = render("buyer@example.invalid", PurchaseRecipient.WITH_ACCOUNT).text();

        assertTrue(body.contains(BASE + "/account/licenses"), "the licence is there to be opened");
        assertFalse(body.contains("/signup"), "they are not asked to sign up again");
    }

    @Test
    @DisplayName("A licence proving an address would not collect says nothing about accounts at all")
    void unstatedSaysNothing() {
        String body = render("buyer@example.invalid", PurchaseRecipient.UNSTATED).text();

        assertFalse(body.contains("/signup"), "no invitation");
        assertFalse(body.contains("/account/licenses"), "and no claim that it is already held");
    }

    @Test
    @DisplayName("Every variant still carries the key and the product's own text")
    void theMailIsStillTheMail() {
        for (PurchaseRecipient recipient : PurchaseRecipient.values()) {
            String body = render("buyer@example.invalid", recipient).text();
            assertTrue(body.contains("LYNA-TEST-KEY-0001"), "the key, for " + recipient);
            assertTrue(body.contains("Thanks for buying Widget."), "the operator's text, for " + recipient);
            assertTrue(body.contains("Workbench"), "the instance's name, for " + recipient);
        }
    }

    @Test
    @DisplayName("An address with characters a URL minds is encoded into the link, not pasted into it")
    void addressIsEncoded() {
        String body = render("ada+kofi@example.invalid", PurchaseRecipient.WITHOUT_ACCOUNT).text();

        assertTrue(body.contains("email=ada%2Bkofi%40example.invalid"),
                "a plus that reaches the signup form as a space loses the purchase");
    }
}
