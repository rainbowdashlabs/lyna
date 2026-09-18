/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.license.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.feature.account.service.AccountLinkService;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.repository.LicenseRepository;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Who holds a licence, and what that means for their roles.
 *
 * <p>The row and the role always move together. A claim that wrote the row and failed to assign the
 * role would leave somebody holding a licence the bot does not believe in, so the two are decided
 * here rather than at whichever command or endpoint happened to ask.
 */
@Singleton
public class LicenseService {
    private static final Logger log = getLogger(LicenseService.class);

    private final LicenseRepository licenses;
    private final AccountLinkService accountLinks;
    private final LicenseSharingService sharing;

    @Inject
    public LicenseService(LicenseRepository licenses, AccountLinkService accountLinks, LicenseSharingService sharing) {
        this.licenses = licenses;
        this.accountLinks = accountLinks;
        this.sharing = sharing;
    }

    /**
     * @return the Discord id of whoever holds it, or zero when nobody does
     */
    public long owner(License license) {
        if (license.cachedOwner() != -1) return license.cachedOwner();
        long owner = licenses.ownerDiscordId(license.id()).orElse(0L);
        license.cachedOwner(owner);
        return owner;
    }

    public boolean isClaimed(License license) {
        return owner(license) != 0;
    }

    /**
     * Gives an unheld licence to whoever asked for it.
     *
     * @return whether they now hold it; false when somebody already did
     */
    public boolean claim(License license, Member member) {
        if (!licenses.claim(accountLinks.accountIdForDiscord(member.getIdLong()), license.id())) return false;
        log.info(
                LogNotify.STATUS,
                "{} claimed license {} for {}",
                member.getEffectiveName(),
                license.id(),
                license.product().name());
        license.cachedOwner(member.getIdLong());
        license.product().assign(member);
        return true;
    }

    /**
     * Moves a licence to somebody else, ending every share of it first.
     *
     * <p>The previous holder keeps the product's role only if something else still grants it.
     */
    public boolean transfer(License license, Member member) {
        long previous = owner(license);
        sharing.clearSharees(license);
        if (!licenses.transfer(accountLinks.accountIdForDiscord(member.getIdLong()), license.id())) return false;
        Member oldOwner = member.getGuild().retrieveMemberById(previous).complete();
        if (oldOwner != null && !license.product().canAccess(oldOwner)) {
            log.info(
                    LogNotify.STATUS,
                    "{} transferred license for {} to {}",
                    oldOwner.getEffectiveName(),
                    license.product().name(),
                    member.getEffectiveName());
            license.product().revoke(oldOwner);
        }
        license.cachedOwner(member.getIdLong());
        license.product().assign(member);
        return true;
    }

    /**
     * Ends a licence, taking the product's role back from whoever held it.
     */
    public boolean delete(License license) {
        sharing.clearSharees(license);
        long owner = owner(license);
        long guildId = license.product().products().licenseGuild().guildId();
        license.product().products().licenseGuild().roles().revoke(guildId, owner, license.product());
        return licenses.delete(license.id());
    }

    public boolean grantAccess(License license, ReleaseType type) {
        return licenses.grantAccess(license.id(), type);
    }

    public List<ReleaseType> access(License license) {
        return licenses.access(license.id());
    }
}
