package de.chojo.lyna.services;

import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.LicenseUser;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RoleListener extends ListenerAdapter {
    private final Guilds guilds;

    public RoleListener(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        LicenseGuild guild = guilds.guild(event.getGuild());
        LicenseUser user = guild.user(event.getMember());
        List<Product> products = user.products();
        for (Product product : products) {
            product.assign(event.getMember());
        }
    }
}
