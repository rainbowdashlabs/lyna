/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.dao.licenses;

import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.feature.license.entity.Sharee;
import de.chojo.lyna.feature.license.repository.LicenseRepository;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

public class License {
    private static final Logger log = getLogger(License.class);

    private static final LicenseRepository REPOSITORY = new LicenseRepository();
    /**
     * The product the license is for.
     */
    private final Product product;

    /**
     * Identifier of the user.
     */
    private final String userIdentifier;

    /**
     * License id
     */
    private final int id;

    /**
     * License key
     */
    private final String key;

    /**
     * The owner owning this license.
     */
    private long owner;

    /**
     * The sub users of the license.
     */
    private final List<Long> subUsers;

    public License(Product product, String userIdentifier, int id, String key) {
        this(product, userIdentifier, id, key, -1, new ArrayList<>());
    }

    public License(Product product, String userIdentifier, int id, String key, long owner, List<Long> subUsers) {
        this.product = product;
        this.userIdentifier = userIdentifier;
        this.id = id;
        this.key = key;
        this.owner = owner;
        this.subUsers = subUsers;
    }

    private int accountIdFor(Member member) {
        return product.products().licenseGuild().guilds().accountLinks().accountIdForDiscord(member.getIdLong());
    }

    public long owner() {
        if (owner != -1) {
            return owner;
        }
        owner = REPOSITORY.ownerDiscordId(id).orElse(0L);
        return owner;
    }

    public List<Long> subUsers() {
        return REPOSITORY.shareeDiscordIds(id);
    }

    /**
     * Everybody the licence is shared with, named.
     *
     * <p>Unlike {@link #subUsers()}, which answers only for the ones Discord can be told about, this
     * counts the people who hold the licence through the web and have no Discord id at all. The role
     * logic wants the former; anything that reports to a person wants this, or it quietly says a
     * licence is shared with fewer people than it is.
     *
     * @return the sharees, those with a Discord id first
     */
    public List<Sharee> sharees() {
        return REPOSITORY.sharees(id);
    }

    /**
     * What the guild's share cap is measured against.
     *
     * <p>An invite nobody has answered holds a place: counting only accepted shares would let
     * somebody invite the world and hand out places as the replies arrived.
     *
     * @return sharees plus invites still standing
     */
    public int shareCount() {
        return REPOSITORY.shareCount(id);
    }

    public String userIdentifier() {
        return userIdentifier;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    public Product product() {
        return product;
    }

    /**
     * Takes the license away, and the product's role with it.
     *
     * <p>The owner's role goes unconditionally: the license granting it is about to stop existing,
     * so there is nothing left to weigh it against.
     *
     * <p>Through {@link #owner()} rather than the field behind it, which is {@code -1} on a license
     * read from the database until something asks - so this used to take the role from member -1.
     */
    public boolean delete() {
        clearSubUsers();
        long owner = owner();
        long guildId = product.products().licenseGuild().guildId();
        product.products().licenseGuild().roles().revoke(guildId, owner, product);
        return REPOSITORY.delete(id);
    }

    public boolean claim(Member member) {
        if (REPOSITORY.claim(accountIdFor(member), id)) {
            log.info(
                    LogNotify.STATUS,
                    "{} claimed license {} for {}",
                    member.getEffectiveName(),
                    id,
                    product().name());
            owner = member.getIdLong();
            product.assign(member);
            return true;
        }
        return false;
    }

    public boolean isClaimed() {
        return owner() != 0;
    }

    public boolean transfer(Member member) {
        clearSubUsers();
        if (REPOSITORY.transfer(accountIdFor(member), id)) {
            Member oldOwner = member.getGuild().retrieveMemberById(owner).complete();
            if (oldOwner != null && !product.canAccess(oldOwner)) {
                log.info(
                        LogNotify.STATUS,
                        "{} transferred license for {} to {}",
                        oldOwner.getEffectiveName(),
                        product.name(),
                        member.getEffectiveName());
                product.revoke(oldOwner);
            }
            owner = member.getIdLong();
            product.assign(member);
            return true;
        }
        return false;
    }

    /**
     * Ends every share of this license, and takes the product's role back from anybody it was the
     * only thing granting.
     *
     * <p>The rows go first and the entitlement is weighed afterwards. Asked the other way round -
     * which is how this used to read - every sharee still held the share being cleared, so every one
     * of them answered "entitled" and nobody ever lost the role.
     */
    public void clearSubUsers() {
        List<Long> sharees = subUsers();

        REPOSITORY.clearSharees(id);

        long guildId = product.products().licenseGuild().guildId();
        for (Long sharee : sharees) {
            product.products().licenseGuild().roles().revokeIfUnentitled(guildId, sharee, product);
        }
    }

    public boolean removeSubUser(Member member) {
        boolean changed = REPOSITORY.removeSharee(id, accountIdFor(member));
        if (changed) {
            if (!product.canAccess(member)) {
                product.revoke(member);
            }
        }
        return changed;
    }

    public boolean addSubUser(Member member) {
        product.assign(member);
        log.info(
                LogNotify.STATUS, "{} shared license for {} with {}", owner, product.name(), member.getEffectiveName());
        return REPOSITORY.addSharee(id, accountIdFor(member));
    }

    public boolean grantAccess(ReleaseType type) {
        return REPOSITORY.grantAccess(id, type);
    }

    public List<ReleaseType> access() {
        return REPOSITORY.access(id);
    }
}
