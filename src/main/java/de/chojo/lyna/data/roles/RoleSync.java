package de.chojo.lyna.data.roles;

import de.chojo.lyna.data.dao.products.Product;

/**
 * Keeps the Discord roles a product grants in step with what its licenses say.
 *
 * <p>Two methods rather than one, because the two places that need this differ: deleting a license
 * takes the role back whatever else is true, while clearing a share takes it back only from somebody
 * nothing else entitles. Collapsing them loses that difference, and with it the role cleanup on
 * deletion.
 *
 * <p>An instance running without the bot has no roles to keep in step, so {@link #NOOP} is what it
 * uses and the license rows change on their own.
 */
public interface RoleSync {
    /**
     * Does nothing, for an instance with no gateway connected.
     */
    RoleSync NOOP = new RoleSync() {
        @Override
        public void revoke(long guildId, long discordId, Product product) {
        }

        @Override
        public void revokeIfUnentitled(long guildId, long discordId, Product product) {
        }
    };

    /**
     * Takes the product's role back, whatever the licenses now say.
     *
     * @param guildId   the guild the role belongs to
     * @param discordId who to take it from
     * @param product   the product whose role it is
     */
    void revoke(long guildId, long discordId, Product product);

    /**
     * Takes the product's role back unless something still entitles that id to it.
     *
     * <p>Call this <em>after</em> the rows that granted it are gone: asked while they are still
     * there, the entitlement check answers yes and nothing is ever taken back.
     */
    void revokeIfUnentitled(long guildId, long discordId, Product product);
}
