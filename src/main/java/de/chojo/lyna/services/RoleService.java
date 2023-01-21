package de.chojo.lyna.services;

import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoleService {

    public void claim(License license) {
        long owner = license.owner();
        List<Long> subusers = license.subUsers();
        Product product = license.product();
        Guild guild = product.products().guild();
        Optional<Role> role = product.role(guild);

        ArrayList<Long> ids = new ArrayList<>(subusers);
        ids.add(owner);

        if (role.isEmpty()) return;

        for (Member member : guild.retrieveMembersByIds(ids).get()) {
            guild.addRoleToMember(member, role.get()).reason("License assigned").queue();
        }
    }

    public void unclaim(License license) {
        long owner = license.owner();
        List<Long> subusers = license.subUsers();
        Product product = license.product();
        Guild guild = product.products().guild();
        Optional<Role> role = product.role(guild);

        ArrayList<Long> ids = new ArrayList<>(subusers);
        ids.add(owner);

        if (role.isEmpty()) return;

        for (Member member : guild.retrieveMembersByIds(ids).get()) {
            guild.removeRoleFromMember(member, role.get()).reason("License revoked").queue();
        }
    }
}
