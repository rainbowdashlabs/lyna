package de.chojo.lyna.gateway;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.List;
import java.util.Optional;

/**
 * What the application can ask Discord, and what it gets when there is no Discord to ask.
 *
 * <p>The bot is optional: with {@code baseSettings.botEnabled} off the HTTP API runs alone, and
 * everything here answers empty. That is a deployment rather than a fault, which is why this is an
 * interface with {@link #NONE} rather than a {@code ShardManager} that might be null - a caller
 * cannot forget to handle the case, because there is no case to handle.
 *
 * <p>Deliberately narrow. It covers what the application actually asks - which guilds exist, who is
 * in one - and nothing else, so an instance running without a gateway is missing only answers it can
 * do without.
 */
public interface Gateway {
    /**
     * A gateway that knows nothing, for an instance running without the bot.
     */
    Gateway NONE = new Gateway() {
        @Override
        public boolean connected() {
            return false;
        }

        @Override
        public Optional<Guild> guild(long guildId) {
            return Optional.empty();
        }

        @Override
        public List<Guild> guilds() {
            return List.of();
        }

        @Override
        public Optional<Member> member(long guildId, long discordId) {
            return Optional.empty();
        }
    };

    /**
     * @return whether there is a gateway at all, for the screens that report on one
     */
    boolean connected();

    /**
     * @param guildId the guild
     * @return the guild, when the bot is in it
     */
    Optional<Guild> guild(long guildId);

    /**
     * @return every guild the bot is in
     */
    List<Guild> guilds();

    /**
     * The member, when the gateway still knows one.
     *
     * <p>Somebody who has left the guild is nobody to act on, and asking about them answers with an
     * error rather than with nothing - which used to abort the caller before it had done its own work.
     *
     * @param guildId   the guild to look in
     * @param discordId who to look for
     * @return the member, if they are there
     */
    Optional<Member> member(long guildId, long discordId);
}
