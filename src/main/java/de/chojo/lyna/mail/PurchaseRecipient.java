package de.chojo.lyna.mail;

/**
 * What the application knows about the person a licence mail is going to.
 *
 * <p>The mail says one of two things beneath the product's own text, and which one it may say is a
 * question about accounts rather than about the product - so it is decided where the licence was
 * handed out, not in the template and not by the operator who wrote the body.
 *
 * <p>The links are not carried here. Every mail already renders with the instance's address, so the
 * template builds them and no caller assembles a URL.
 */
public enum PurchaseRecipient {
    /**
     * Nobody has proved the address this was bought from.
     *
     * <p>The mail invites them to sign up with it, because that is what hands them the licence:
     * proving the address collects a purchase made with it.
     */
    WITHOUT_ACCOUNT,
    /**
     * The licence is already held by the account that proved the address.
     *
     * <p>The mail points at the account rather than at the key.
     */
    WITH_ACCOUNT,
    /**
     * Says nothing about accounts.
     *
     * <p>For a mail that is a sample, and for a licence that would not be collected by proving the
     * address - a hand-issued one, or a receipt parsed out of the mailbox. Promising either thing
     * there would be a promise the application does not keep.
     */
    UNSTATED
}
