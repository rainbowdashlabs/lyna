/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.license.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.feature.account.service.AccountLinkService;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.entity.Sharee;
import de.chojo.lyna.feature.license.repository.LicenseRepository;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Who a licence is shared with.
 *
 * <p>Every change here weighs the product's role afterwards rather than assuming it: a sharee who
 * loses one share may still hold another, and taking the role from them would be wrong.
 */
@Singleton
public class LicenseSharingService {
    private static final Logger log = getLogger(LicenseSharingService.class);

    private final LicenseRepository licenses;
    private final AccountLinkService accountLinks;

    @Inject
    public LicenseSharingService(LicenseRepository licenses, AccountLinkService accountLinks) {
        this.licenses = licenses;
        this.accountLinks = accountLinks;
    }

    /**
     * @return everybody the licence is shared with, including those with no Discord id
     */
    public List<Sharee> sharees(License license) {
        return licenses.sharees(license.id());
    }

    /**
     * @return the sharees the bot can act on, which is the ones Discord knows
     */
    public List<Long> shareeDiscordIds(License license) {
        return licenses.shareeDiscordIds(license.id());
    }

    /**
     * @return sharees plus invites still standing, which is what the share cap is measured against
     */
    public int shareCount(License license) {
        return licenses.shareCount(license.id());
    }

    public boolean addSharee(License license, Member member) {
        license.product().assign(member);
        log.info(
                LogNotify.STATUS,
                "{} shared license for {} with {}",
                license.cachedOwner(),
                license.product().name(),
                member.getEffectiveName());
        return licenses.addSharee(license.id(), accountLinks.accountIdForDiscord(member.getIdLong()));
    }

    public boolean removeSharee(License license, Member member) {
        boolean changed = licenses.removeSharee(license.id(), accountLinks.accountIdForDiscord(member.getIdLong()));
        if (changed && !license.product().canAccess(member)) {
            license.product().revoke(member);
        }
        return changed;
    }

    /**
     * Ends every share of this licence, and takes the product's role back from anybody it was the
     * only thing granting.
     *
     * <p>The rows go first and the entitlement is weighed afterwards. Asked the other way round -
     * which is how this used to read - every sharee still held the share being cleared, so every one
     * of them answered "entitled" and nobody ever lost the role.
     */
    public void clearSharees(License license) {
        List<Long> sharees = shareeDiscordIds(license);
        licenses.clearSharees(license.id());
        long guildId = license.product().products().licenseGuild().guildId();
        for (Long sharee : sharees) {
            license.product().products().licenseGuild().roles().revokeIfUnentitled(guildId, sharee, license.product());
        }
    }
}
