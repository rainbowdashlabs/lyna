/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.google.inject.Inject;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.guild.LicenseGuild;
import de.chojo.lyna.feature.instance.repository.InstanceOperatorRepository;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.gateway.Gateway;
import de.chojo.lyna.web.api.auth.Auth;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.Optional;

/**
 * Who may administer the guild a request names, for every admin endpoint.
 *
 * <p>An operator administers every guild. Otherwise the caller needs Manage Server, or the guild's
 * admin role, on a guild the gateway knows. With no gateway there is no membership to check, so only
 * an operator gets in; the database answers whether or not a bot is connected, and the few operations
 * that act on Discord itself find no guild and say so.
 *
 * <p>Each method that refuses has already answered the request, and returns null.
 */
public class GuildAdminGuard {
    private final Auth auth;
    private final Conf configuration;
    private final AccountRepository accounts;
    private final Guilds guilds;
    private final InstanceOperatorRepository operators;
    private final Gateway gateway;

    /**
     * @param callerDiscordId the caller's Discord id, null when they never linked one
     */
    public record GuildAdmin(LicenseGuild guild, Long callerDiscordId, boolean operator) {}

    @Inject
    public GuildAdminGuard(
            Auth auth,
            Conf configuration,
            AccountRepository accounts,
            Guilds guilds,
            InstanceOperatorRepository operators,
            Gateway gateway) {
        this.auth = auth;
        this.configuration = configuration;
        this.accounts = accounts;
        this.guilds = guilds;
        this.operators = operators;
        this.gateway = gateway;
    }

    /**
     * @return the guild the path names, when the caller may administer it
     */
    public GuildAdmin require(Context ctx) {
        Optional<JwtService.Verified> session = auth.currentSession(ctx);
        if (session.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            return null;
        }
        long guildId;
        try {
            guildId = Long.parseLong(ctx.pathParam("guildId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid guild id");
            return null;
        }
        Long discordId = discordId(session.get());
        boolean operator = isOperator(discordId);
        Guild guild = gateway.guild(guildId).orElse(null);
        if (guild == null) {
            if (!operator) {
                ctx.status(HttpStatus.NOT_FOUND);
                return null;
            }
            return new GuildAdmin(guilds.guild(guildId), discordId, true);
        }
        if (!operator && !hasGuildAdmin(discordId, guild)) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
        return new GuildAdmin(guilds.guild(guild), discordId, operator);
    }

    /**
     * @return the product the path names, within the guild being administered
     */
    public Product product(Context ctx, GuildAdmin admin) {
        int productId;
        try {
            productId = Integer.parseInt(ctx.pathParam("productId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
        Optional<Product> product = admin.guild().products().byId(productId);
        if (product.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return null;
        }
        return product.get();
    }

    /**
     * @return the caller's Discord id, from the session or the account's link, or null
     */
    public Long discordId(JwtService.Verified verified) {
        if (verified.discordId() != null) return verified.discordId();
        return accounts.findLinkByAccountId(verified.accountId())
                .map(AccountIdentity::externalIdAsLong)
                .orElse(null);
    }

    public boolean isOperator(Long discordId) {
        if (discordId == null) return false;
        return configuration.main().baseSettings().isOwner(discordId) || operators.contains(discordId);
    }

    /**
     * Whether the caller has Manage Server, or the guild's admin role, on a guild the gateway knows.
     *
     * <p>The member is asked of Discord when the cache does not hold them. The bot does not load a
     * guild's members up front, so a cache-only lookup missed even the guild's owner, who then could
     * not reach the admin area at all.
     */
    public boolean hasGuildAdmin(Long discordId, Guild guild) {
        if (discordId == null) return false;
        Member member = gateway.member(guild.getIdLong(), discordId).orElse(null);
        if (member == null) return false;
        if (member.hasPermission(Permission.MANAGE_SERVER)) return true;
        Long adminRole = guilds.guild(guild).settings().license().adminRoleId();
        if (adminRole == null) return false;
        return member.getRoles().stream().anyMatch(role -> role.getIdLong() == adminRole);
    }
}
