package de.chojo.lyna.data.dao.products;

import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.downloads.Downloads;
import de.chojo.nexus.NexusRest;
import de.chojo.sadu.types.PostgreSqlTypes;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Product {
    private final Products products;
    private final NexusRest nexus;
    int id;
    String name;
    String url;
    long role;
    Downloads downloads;
    boolean free;

    public Product(Products products, int id, String name, String url, long role, boolean free) {
        this.products = products;
        this.nexus = products.nexus();
        this.id = id;
        this.name = name;
        this.url = url;
        this.role = role;
        this.free = free;
        downloads = new Downloads(this);
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String url() {
        return url;
    }

    public long role() {
        return role;
    }

    public boolean delete() {
        return builder().query("DELETE FROM product WHERE id = ? AND guild_id = ?")
                .parameter(stmt -> stmt.setInt(id).setLong(products.guildId()))
                .delete()
                .sendSync()
                .changed();
    }

    public long guildId() {
        return products.guildId();
    }

    public Products products() {
        return products;
    }

    public boolean canAccess(Member member) {
        if (free) return true;
        boolean hasLicense = products.licenseGuild().user(member).canAccess(this);
        boolean hasRole = builder(ReleaseType.class)
                                  .query("SELECT release_type FROM role_access WHERE (product_id = ? OR  product_id = 0) AND ARRAY[role_id] && ?")
                                  .parameter(stmt -> stmt.setInt(id).setArray(member.getRoles().stream().map(Role::getIdLong).toList(), PostgreSqlTypes.BIGINT))
                                  .readRow(row -> row.getEnum("release_type", ReleaseType.class))
                                  .allSync().size() > 0;
        return hasLicense || hasRole;
    }

    public Set<ReleaseType> availableReleaseTypes(Member member) {
        if (free) {
            return Set.of(ReleaseType.values());
        }
        List<ReleaseType> byUser = builder(ReleaseType.class)
                .query("SELECT release_type FROM user_product_access WHERE user_id = ? AND product_id = ?")
                .parameter(stmt -> stmt.setLong(member.getIdLong()).setInt(id))
                .readRow(row -> row.getEnum("release_type", ReleaseType.class))
                .allSync();

        List<ReleaseType> byRole = builder(ReleaseType.class)
                .query("SELECT release_type FROM role_access WHERE (product_id = ? OR  product_id = 0) AND ARRAY[role_id] && ?")
                .parameter(stmt -> stmt.setInt(id).setArray(member.getRoles().stream().map(Role::getIdLong).toList(), PostgreSqlTypes.BIGINT))
                .readRow(row -> row.getEnum("release_type", ReleaseType.class))
                .allSync();
        var result = EnumSet.noneOf(ReleaseType.class);
        result.addAll(byUser);
        result.addAll(byRole);
        return result;
    }

    public Optional<Role> role(Guild guild) {
        return Optional.ofNullable(guild.getRoleById(role));
    }

    public void assign(Member member) {
        Role roleById = member.getGuild().getRoleById(role);
        if (roleById != null && !member.getRoles().contains(roleById)) {
            member.getGuild().addRoleToMember(member, roleById).queue();
        }
    }

    public Downloads downloads() {
        return downloads;
    }

    public void revoke(Member member) {
        Role roleById = member.getGuild().getRoleById(role);
        if (roleById != null && member.getRoles().contains(roleById)) {
            member.getGuild().removeRoleFromMember(member, roleById).queue();
        }
    }

    public Guild guild() {
        return products().guild();
    }

    public NexusRest nexus() {
        return nexus;
    }

    public List<License> license(Member member) {
        return builder(Integer.class)
                .query("""
                        SELECT
                        	guild_id,
                        	user_id,
                        	product_id,
                        	platform_id,
                        	license_id,
                        	user_identifier,
                        	key
                        FROM
                        	user_license_all
                        WHERE guild_id = ?
                          AND product_id = ?
                          AND user_id = ?""")
                .parameter(stmt -> stmt.setLong(guildId()).setInt(id).setLong(member.getIdLong()))
                .readRow(row -> row.getInt("license_id"))
                .allSync()
                .stream()
                .map(i -> products.licenseGuild().licenses().byId(i))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
