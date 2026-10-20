/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.service;

import com.google.inject.Inject;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.account.repository.AccountRoleRepository;
import de.chojo.lyna.feature.product.repository.ProductRepository;
import de.chojo.lyna.gateway.Gateway;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Keeps the stored roles of linked accounts in step with what they wear on Discord.
 *
 * <p>Only guilds that have products are mirrored, and only while the gateway can see them: a guild
 * the bot cannot reach right now says nothing about who is in it, so its rows are left as they are
 * rather than cleared. A Discord failure is logged and leaves the rows alone for the same reason.
 */
public class AccountRoleSync {
    private static final Logger log = getLogger(AccountRoleSync.class);
    private static final int CHUNK = 100;

    private final AccountRoleRepository roles;
    private final AccountRepository accounts;
    private final ProductRepository products;
    private final Gateway gateway;

    @Inject
    public AccountRoleSync(
            AccountRoleRepository roles, AccountRepository accounts, ProductRepository products, Gateway gateway) {
        this.roles = roles;
        this.accounts = accounts;
        this.products = products;
        this.gateway = gateway;
    }

    /**
     * Refreshes one account from Discord, which is what signing in does so that logging out and in
     * again fixes access that looks wrong.
     */
    public void syncAccount(int accountId) {
        Optional<Long> discordId = accounts.findLinkByAccountId(accountId).map(AccountIdentity::externalIdAsLong);
        if (discordId.isEmpty()) return;
        for (Guild guild : reachableGuilds()) {
            try {
                gateway.member(guild.getIdLong(), discordId.get())
                        .ifPresentOrElse(
                                member -> roles.replaceDiscordRoles(accountId, guild.getIdLong(), roleIds(member)),
                                () -> roles.clearDiscordRoles(accountId, guild.getIdLong()));
            } catch (RuntimeException e) {
                log.warn("Could not read the roles of account {} in guild {}", accountId, guild.getIdLong(), e);
            }
        }
    }

    /**
     * Refreshes every linked account, asking the gateway for members a hundred at a time.
     */
    public void syncAll() {
        Map<Long, Integer> linked = accounts.discordAccounts();
        if (linked.isEmpty()) return;
        for (Guild guild : reachableGuilds()) {
            for (List<Long> chunk : chunks(linked.keySet())) {
                try {
                    syncChunk(guild, chunk, linked);
                } catch (RuntimeException e) {
                    log.warn("Could not read the members of guild {}", guild.getIdLong(), e);
                }
            }
        }
    }

    /**
     * Takes the roles a member now wears, as Discord sent them.
     */
    public void memberUpdated(long guildId, long discordId, Collection<Long> roleIds) {
        if (!mirrored(guildId)) return;
        accountOf(discordId).ifPresent(account -> roles.replaceDiscordRoles(account, guildId, roleIds));
    }

    /**
     * Forgets the roles of somebody who left the guild.
     */
    public void memberLeft(long guildId, long discordId) {
        accountOf(discordId).ifPresent(account -> roles.clearDiscordRoles(account, guildId));
    }

    /**
     * Forgets a role the guild deleted.
     */
    public void roleDeleted(long roleId) {
        roles.removeRole(roleId);
    }

    /**
     * Forgets the Discord roles of an account that no longer has Discord linked.
     */
    public void unlinked(int accountId) {
        roles.clearDiscordRoles(accountId);
    }

    private void syncChunk(Guild guild, List<Long> chunk, Map<Long, Integer> linked) {
        List<Member> members = guild.retrieveMembersByIds(chunk).get();
        Set<Long> present = new HashSet<>();
        for (Member member : members) {
            present.add(member.getIdLong());
            roles.replaceDiscordRoles(linked.get(member.getIdLong()), guild.getIdLong(), roleIds(member));
        }
        chunk.stream()
                .filter(id -> !present.contains(id))
                .forEach(id -> roles.clearDiscordRoles(linked.get(id), guild.getIdLong()));
    }

    private List<Guild> reachableGuilds() {
        return products.guildsWithProducts().stream()
                .flatMap(id -> gateway.guild(id).stream())
                .toList();
    }

    private boolean mirrored(long guildId) {
        return products.guildsWithProducts().contains(guildId);
    }

    private Optional<Integer> accountOf(long discordId) {
        return accounts.accountIdForIdentity(AccountIdentity.DISCORD, Long.toString(discordId));
    }

    private static List<Long> roleIds(Member member) {
        return member.getRoles().stream().map(ISnowflake::getIdLong).toList();
    }

    private static List<List<Long>> chunks(Collection<Long> ids) {
        List<Long> all = new ArrayList<>(ids);
        List<List<Long>> chunks = new ArrayList<>();
        for (int start = 0; start < all.size(); start += CHUNK) {
            chunks.add(all.subList(start, Math.min(start + CHUNK, all.size())));
        }
        return chunks;
    }
}
