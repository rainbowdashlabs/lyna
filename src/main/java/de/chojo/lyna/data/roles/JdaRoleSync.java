package de.chojo.lyna.data.roles;

import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.util.Optional;

/**
 * Role cleanup through the gateway.
 */
public class JdaRoleSync implements RoleSync {
    private final ShardManager shardManager;

    public JdaRoleSync(ShardManager shardManager) {
        this.shardManager = shardManager;
    }

    @Override
    public void revoke(long guildId, long discordId, Product product) {
        member(guildId, discordId).ifPresent(product::revoke);
    }

    @Override
    public void revokeIfUnentitled(long guildId, long discordId, Product product) {
        member(guildId, discordId)
                .filter(member -> !product.canAccess(member))
                .ifPresent(product::revoke);
    }

    /**
     * The member, when the gateway still knows one.
     *
     * <p>Somebody who has left the guild is nothing to take a role from, and asking about them
     * answers with an error rather than with nothing - which used to abort the caller before it had
     * done its own work.
     */
    private Optional<Member> member(long guildId, long discordId) {
        Guild guild = shardManager.getGuildById(guildId);
        if (guild == null) return Optional.empty();
        Member cached = guild.getMemberById(discordId);
        if (cached != null) return Optional.of(cached);
        try {
            return Optional.ofNullable(guild.retrieveMemberById(discordId).complete());
        } catch (ErrorResponseException e) {
            if (e.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER
                    || e.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
