package de.chojo.lyna.data.dao.products;

import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.downloads.Download;
import de.chojo.lyna.data.dao.products.downloads.Downloads;
import de.chojo.lyna.util.Version;
import de.chojo.nexus.NexusRest;
import de.chojo.nexus.entities.PageComponentXO;
import de.chojo.nexus.requests.v1.search.Direction;
import de.chojo.nexus.requests.v1.search.Sort;
import de.chojo.sadu.exceptions.ThrowingConsumer;
import de.chojo.sadu.types.PostgreSqlTypes;
import de.chojo.sadu.wrapper.util.ParamBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Product {
    private final Products products;
    private final NexusRest nexus;
    private final int id;
    private String name;
    private String url;
    private long role;
    private final Downloads downloads;
    private boolean free;
    private boolean trial;

    public Product(Products products, int id, String name, String url, long role, boolean free, boolean trial) {
        this.products = products;
        this.nexus = products.nexus();
        this.id = id;
        this.name = name;
        this.url = url;
        this.role = role;
        this.free = free;
        this.trial = trial;
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
        return products.licenseGuild().user(member).canAccess(this);
    }

    public boolean hasTrial(Member member) {
        return builder(Boolean.class).query("SELECT NOT exists(SELECT 1 FROM trial WHERE product_id = ? AND user_id = ?) as exists")
                .parameter(stmt -> stmt.setInt(id).setLong(member.getIdLong()))
                .readRow(row -> row.getBoolean("exists"))
                .firstSync()
                .orElse(false);
    }

    public void claimTrial(Member member) {
        builder().query("INSERT INTO trial(product_id, user_id) VALUES(?,?) ON CONFLICT DO NOTHING")
                .parameter(stmt -> stmt.setInt(id).setLong(member.getIdLong()))
                .insert()
                .sendSync();
    }


    public boolean canDownload(Member member) {
        if (free) return true;
        return !availableReleaseTypes(member).isEmpty();
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

    public Optional<Version> latestVersion(Set<ReleaseType> types) {
        var downloads = downloads().downloads().stream()
                .filter(download -> types.contains(download.type().releaseType()))
                .toList();
        if (downloads.isEmpty()) {
            return Optional.empty();
        }

        List<Version> assets = new ArrayList<>();
        for (Download download : downloads) {
            PageComponentXO complete = products().nexus().v1().search().search()
                    .repository(download.repository())
                    .mavenGroupId(download.groupId())
                    .mavenArtifactId(download.artifactId())
                    .sort(Sort.VERSION)
                    .direction(Direction.DESC)
                    .complete();
            if (complete.isEmpty()) continue;
            assets.add(Version.parse(complete.items().get(0).version()));
        }
        return assets.stream().max(Version::compareTo);
    }

    public boolean free() {
        return free;
    }

    public boolean trial() {
        return trial;
    }

    public void name(String name) {
        if (set("name", stmt -> stmt.setString(name))) {
            this.name = name;
        }
    }

    public void url(String url) {
        if (set("url", stmt -> stmt.setString(url))) {
            this.url = url;
        }
    }

    public void role(long role) {
        if (set("role", stmt -> stmt.setLong(role))) {
            this.role = role;
        }
    }

    public void free(boolean free) {
        if (set("free", stmt -> stmt.setBoolean(free))) {
            this.free = free;
        }
    }

    public void trial(boolean trial) {
        if (set("trial", stmt -> stmt.setBoolean(trial))) {
            this.trial = trial;
        }
    }

    private boolean set(String column, ThrowingConsumer<ParamBuilder, SQLException> consumer) {
        return builder().query("""
                        UPDATE
                            product
                        SET %s = ?
                        WHERE
                            id = ?""", column)
                .parameter(stmt -> {
                    consumer.accept(stmt);
                    stmt.setInt(id);
                }).update()
                .sendSync()
                .changed();
    }

    public void role(Role role) {
        if (role.isPublicRole()) {
            role(0);
            return;
        }
        role(role.getIdLong());
    }
}
