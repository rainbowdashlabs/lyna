/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.mail;

import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.TestConf;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.feature.mail.repository.MailingLookup;
import de.chojo.lyna.feature.purchase.service.PurchaseService;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Which mails are taken as payment receipts.
 *
 * <p>A receipt grants a licence to the address written inside it, so what is let through this door
 * decides who can be granted one. The parsing is {@link PayPalMailTest}'s; this is only about the
 * door.
 *
 * <p>What a test observes is whether the handler got as far as looking the product up. Beyond that
 * point the mail has been believed, which is the thing being asserted.
 */
class MailHandlerTest {
    private static final String PAYPAL = "service@paypal.de";
    private static final String FORWARDER = "shop@example.invalid";

    private final MailingLookup mailings = Mockito.mock(MailingLookup.class);

    private MailingLookup handle(String from, String forwardedBy, String yaml) throws Exception {
        Mockito.when(mailings.byName(anyString())).thenReturn(Optional.empty());
        Conf configuration = TestConf.from(yaml);
        var handler = new MailHandler(
                mailings,
                Mockito.mock(MailingService.class),
                Mockito.mock(PurchaseService.class),
                configuration,
                Mockito.mock(LicenseService.class));
        handler.accept(receipt(from, forwardedBy));
        return mailings;
    }

    /** A real PayPal receipt, from whoever the test says and forwarded by whoever it says. */
    private MimeMessage receipt(String from, String forwardedBy) throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(from));
        message.setSubject("Receipt");
        message.setContent(read("/mail.html"), "text/html; charset=UTF-8");
        if (forwardedBy != null) {
            message.setHeader("X-Forwarded-For", forwardedBy);
        }
        message.saveChanges();
        return message;
    }

    private static String read(String resource) throws IOException {
        try (var in = MailHandlerTest.class.getResourceAsStream(resource)) {
            return new String(in.readAllBytes());
        }
    }

    private static final String TRUSTS_NOBODY = "mailing:\n  enabled: true\n";
    private static final String TRUSTS_FORWARDER =
            "mailing:\n  enabled: true\n  originMail:\n    - \"" + FORWARDER + "\"\n";
    private static final String TRUSTS_ANYONE = "mailing:\n  enabled: true\n  skipVerify: true\n";

    @Test
    @DisplayName("A receipt straight from PayPal is a receipt")
    void directFromPaypalIsTaken() throws Exception {
        verify(handle(PAYPAL, null, TRUSTS_NOBODY)).byName(anyString());
    }

    @Test
    @DisplayName("A mail from anybody else is not")
    void anotherSenderIsRefused() throws Exception {
        verify(handle("stranger@example.invalid", null, TRUSTS_NOBODY), never()).byName(anyString());
    }

    @Test
    @DisplayName("A forwarded receipt is refused while no forwarder is trusted")
    void forwardedByNobodyTrustedIsRefused() throws Exception {
        verify(handle(PAYPAL, FORWARDER, TRUSTS_NOBODY), never()).byName(anyString());
    }

    @Test
    @DisplayName("A receipt forwarded by a trusted address is taken")
    void forwardedByATrustedAddressIsTaken() throws Exception {
        verify(handle(PAYPAL, FORWARDER, TRUSTS_FORWARDER)).byName(anyString());
    }

    @Test
    @DisplayName("A receipt forwarded by somebody else is refused even so")
    void forwardedBySomebodyElseIsRefused() throws Exception {
        verify(handle(PAYPAL, "someone@example.invalid", TRUSTS_FORWARDER), never())
                .byName(anyString());
    }

    @Test
    @DisplayName("Skipping verification takes a receipt from anybody, which is what it is for")
    void skipVerifyTakesAnything() throws Exception {
        verify(handle("stranger@example.invalid", null, TRUSTS_ANYONE)).byName(anyString());
    }
}
