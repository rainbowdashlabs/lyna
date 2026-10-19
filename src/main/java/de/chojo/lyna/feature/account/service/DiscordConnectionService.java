/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.auth.DiscordOAuthClient;
import de.chojo.lyna.auth.TokenCipher;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.entity.DiscordToken;
import de.chojo.lyna.feature.account.repository.DiscordTokenRepository;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The connection to Discord an account keeps once it has signed in there: the grant, refreshed before
 * it runs out, and what Discord says about the person behind it.
 *
 * <p>Discord access tokens last a week. Each one is refreshed once it has less than
 * {@link #REFRESH_WINDOW} left, and every refresh reads the person again, so a new handle or a newly
 * verified address arrives without them signing in. A grant Discord no longer honours - the
 * application was removed from their Discord account - and one that can no longer be decrypted are
 * both dropped; the next Discord sign-in stores a new one.
 */
@Singleton
public class DiscordConnectionService {
    static final Duration REFRESH_WINDOW = Duration.ofHours(48);
    private static final Logger log = getLogger(DiscordConnectionService.class);

    private final DiscordTokenRepository tokens;
    private final TokenCipher cipher;
    private final DiscordOAuthClient discord;
    private final AccountLinkService links;
    private final AccountEmailService emails;

    @Inject
    public DiscordConnectionService(
            DiscordTokenRepository tokens,
            TokenCipher cipher,
            DiscordOAuthClient discord,
            AccountLinkService links,
            AccountEmailService emails) {
        this.tokens = tokens;
        this.cipher = cipher;
        this.discord = discord;
        this.links = links;
        this.emails = emails;
    }

    /**
     * Keeps what Discord granted an account. A grant without a refresh token cannot be kept alive,
     * and is not kept at all.
     */
    public void store(int accountId, DiscordOAuthClient.Grant grant) {
        if (grant.refreshToken() == null || grant.refreshToken().isBlank()) return;
        String context = context(accountId);
        tokens.save(
                accountId,
                cipher.seal(grant.accessToken(), context),
                cipher.seal(grant.refreshToken(), context),
                grant.scope(),
                grant.expiresAt(Instant.now()));
    }

    /**
     * Forgets an account's grant, for when Discord is unlinked from it.
     */
    public void forget(int accountId) {
        tokens.delete(accountId);
    }

    /**
     * Takes on what Discord says about the person: the handle the account is named after, and the
     * address on their Discord account when Discord has verified it.
     *
     * <p>Only a verified address. An unverified one is a string somebody typed into Discord, and
     * proving it here would hand them the licences bought with it - which is the whole point of a
     * proved address. One another account already holds is left with that account rather than taken.
     */
    public void remember(int accountId, DiscordOAuthClient.DiscordUser user) {
        links.rememberHandle(user.id(), user.handle());
        user.provedEmail().ifPresent(email -> {
            try {
                emails.confirm(accountId, email);
            } catch (IllegalStateException e) {
                log.info("Not attaching {} from Discord to account {}: {}", email, accountId, e.getMessage());
            }
        });
    }

    /**
     * Refreshes every grant running out within {@link #REFRESH_WINDOW}.
     *
     * @return how many were refreshed
     */
    public int refreshExpiring() {
        int refreshed = 0;
        for (DiscordToken token : tokens.expiringBefore(Instant.now().plus(REFRESH_WINDOW))) {
            if (refresh(token)) refreshed++;
        }
        return refreshed;
    }

    private boolean refresh(DiscordToken token) {
        int accountId = token.accountId();
        Optional<AccountIdentity> link = links.discordIdentity(accountId);
        if (link.isEmpty()) {
            tokens.delete(accountId);
            return false;
        }
        String refreshToken;
        try {
            refreshToken = cipher.open(token.refreshToken(), context(accountId));
        } catch (TokenCipher.Unreadable e) {
            tokens.delete(accountId);
            log.info("Dropped the Discord grant of account {}: it cannot be decrypted with the current key", accountId);
            return false;
        }
        DiscordOAuthClient.Grant grant;
        try {
            grant = discord.refresh(refreshToken);
        } catch (DiscordOAuthClient.InvalidGrantException e) {
            tokens.delete(accountId);
            log.info("Discord no longer honours the grant of account {}; dropped it", accountId);
            return false;
        } catch (Exception e) {
            log.warn("Could not refresh the Discord grant of account {}", accountId, e);
            return false;
        }
        store(accountId, grant);
        rereadUser(accountId, link.get(), grant);
        return true;
    }

    private void rereadUser(int accountId, AccountIdentity link, DiscordOAuthClient.Grant grant) {
        DiscordOAuthClient.DiscordUser user;
        try {
            user = discord.fetchUser(grant.accessToken());
        } catch (Exception e) {
            log.warn("Could not read the Discord user of account {} after refreshing", accountId, e);
            return;
        }
        if (user.id() != link.externalIdAsLong()) {
            tokens.delete(accountId);
            log.info("The Discord grant of account {} names somebody it is not linked to; dropped it", accountId);
            return;
        }
        remember(accountId, user);
    }

    private static String context(int accountId) {
        return "discord_token:" + accountId;
    }
}
