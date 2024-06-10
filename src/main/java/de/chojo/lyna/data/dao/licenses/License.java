package de.chojo.lyna.data.dao.licenses;

import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.slf4j.LoggerFactory.getLogger;

public class License {
    private static final Logger log = getLogger(License.class);
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

    public long owner() {
        if (owner != -1) {
            return owner;
        }
        owner = query("SELECT user_id, license_id FROM user_license WHERE license_id = ?")
                .single(call().bind(id))
                .map(row -> row.getLong("user_id"))
                .first().orElse(0L);
        return owner;
    }

    public List<Long> subUsers() {
        return query("SELECT user_id, license_id FROM user_sub_license WHERE license_id = ?")
                .single(call().bind(id))
                .map(row -> row.getLong("user_id"))
                .all();
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

    public boolean delete() {
        clearSubUsers();
        Member complete = product.guild().retrieveMemberById(owner).complete();
        if (complete != null) {
            product.revoke(complete);
        }
        return query("DELETE FROM license WHERE id = ?")
                .single(call().bind(id))
                .delete()
                .changed();
    }

    public boolean claim(Member member) {
        if (query("INSERT INTO user_license(user_id, license_id) VALUES(?,?) ON CONFLICT DO NOTHING")
                .single(call().bind(member.getIdLong()).bind(id))
                .insert()
                .changed()) {
            log.info(LogNotify.STATUS, "{} claimed license {} for {}", member.getEffectiveName(), id, product().name());
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
        if (query("INSERT INTO user_license(user_id, license_id) VALUES(?,?) ON CONFLICT(license_id) DO UPDATE SET user_id = excluded.user_id")
                .single(call().bind(member.getIdLong()).bind(id))
                .insert()
                .changed()) {
            Member oldOwner = member.getGuild().retrieveMemberById(owner).complete();
            if (oldOwner != null && !product.canAccess(oldOwner)) {
                log.info(LogNotify.STATUS, "{} transferred license for {} to {}", oldOwner.getEffectiveName(), product.name(), member.getEffectiveName());
                product.revoke(oldOwner);
            }
            owner = member.getIdLong();
            product.assign(member);
            return true;
        }
        return false;
    }

    public void clearSubUsers() {
        for (Long subUser : subUsers()) {
            Member complete = product.guild().retrieveMemberById(subUser).complete();
            if (complete != null && !product.canAccess(complete)) {
                product.revoke(complete);
            }
        }

        query("DELETE FROM user_sub_license WHERE license_id = ?")
                .single(call().bind(id()))
                .delete();
    }

    public boolean removeSubUser(Member member) {
        boolean changed = query("DELETE FROM user_sub_license WHERE license_id = ? AND user_id = ?")
                .single(call().bind(id()).bind(member.getIdLong()))
                .delete()
                .changed();
        if (changed) {
            if (!product.canAccess(member)) {
                product.revoke(member);
            }
        }
        return changed;
    }

    public boolean addSubUser(Member member) {
        product.assign(member);
        log.info(LogNotify.STATUS, "{} shared license for {} with {}", owner, product.name(), member.getEffectiveName());
        return query("INSERT INTO user_sub_license(user_id, license_id) VALUES (?,?) ON CONFLICT DO NOTHING")
                .single(call().bind(member.getIdLong()).bind(id()))
                .insert()
                .changed();
    }

    public boolean grantAccess(ReleaseType type) {
        return query("INSERT INTO license_access(license_id, release_type) VALUES (?,?::RELEASE_TYPE) ON CONFLICT DO NOTHING")
                .single(call().bind(id).bind(type))
                .insert()
                .changed();
    }

    public List<ReleaseType> access() {
        return query("SELECT release_type FROM license_access WHERE license_id = ?")
                .single(call().bind(id))
                .map(row -> row.getEnum("release_type", ReleaseType.class))
                .all();
    }
}
