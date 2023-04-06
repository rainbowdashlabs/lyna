package de.chojo.lyna.mail;

import de.chojo.lyna.data.dao.products.mailings.Mailing;

public final class MailCreator {
    public static Mail createLicenseMessage(Mailing mailing, String key, String name, String address) {
        String text = mailing.mailText();
        text = text.replace("{{ name }}", name);
        text = text.replace("{{ key }}", key);

        return new Mail(address, "Thank you for purchasing " + mailing.product().name(), text);
    }
}
