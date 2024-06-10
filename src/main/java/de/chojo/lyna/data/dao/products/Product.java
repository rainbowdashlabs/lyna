package de.chojo.lyna.data.dao.products;

import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.downloads.Download;
import de.chojo.lyna.data.dao.products.downloads.Downloads;
import de.chojo.lyna.data.dao.products.mailings.Mailings;
import de.chojo.lyna.util.Version;
import de.chojo.nexus.NexusRest;
import de.chojo.nexus.entities.PageComponentXO;
import de.chojo.nexus.requests.v1.search.Direction;
import de.chojo.nexus.requests.v1.search.Sort;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Product {
    private final Products products;
    private final NexusRest nexus;
    private final int id;
    private String name;
    private String url;
    private long role;
    private final Downloads downloads;
    private final Mailings mailings;
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
        mailings = new Mailings(this);
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
        return query("DELETE FROM product WHERE id = ? AND guild_id = ?")
                .single(call().bind(id).bind(products.guildId()))
                .delete()
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
        return query("SELECT NOT exists(SELECT 1 FROM trial WHERE product_id = ? AND user_id = ?) as exists")
                .single(call().bind(id).bind(member.getIdLong()))
                .map(row -> row.getBoolean("exists"))
                .first()
                .orElse(false);
    }

    public void claimTrial(Member member) {
        query("INSERT INTO trial(product_id, user_id) VALUES(?,?) ON CONFLICT DO NOTHING")
                .single(call().bind(id).bind(member.getIdLong()))
                .insert();
    }


    public boolean canDownload(Member member) {
        if (free) return true;
        return !availableReleaseTypes(member).isEmpty();
    }

    public Set<ReleaseType> availableReleaseTypes(Member member) {
        if (free) {
            return Set.of(ReleaseType.values());
        }
        List<ReleaseType> byUser =
                query("SELECT release_type FROM user_product_access WHERE user_id = ? AND product_id = ?")
                        .single(call().bind(member.getIdLong()).bind(id))
                        .map(row -> row.getEnum("release_type", ReleaseType.class))
                        .all();

        List<ReleaseType> byRole =
                query("SELECT release_type FROM role_access WHERE (product_id = ? OR  product_id = 0) AND ARRAY[role_id] && ?")
                        .single(call().bind(id).bind(member.getRoles().stream().map(Role::getIdLong).toList(), PostgreSqlTypes.BIGINT))
                        .map(row -> row.getEnum("release_type", ReleaseType.class))
                        .all();
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
        return query("""
                SELECT
                	guild_id,
                	user_id,
                	product_id,
                	license_id,
                	user_identifier,
                	key
                FROM
                	user_license_all
                WHERE guild_id = ?
                  AND product_id = ?
                  AND user_id = ?""")
                .single(call().bind(guildId()).bind(id).bind(member.getIdLong()))
                .map(row -> row.getInt("license_id"))
                .all()
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
        if (set("name", stmt -> stmt.bind(name))) {
            this.name = name;
        }
    }

    public void url(String url) {
        if (set("url", stmt -> stmt.bind(url))) {
            this.url = url;
        }
    }

    public void role(long role) {
        if (set("role", stmt -> stmt.bind(role))) {
            this.role = role;
        }
    }

    public void free(boolean free) {
        if (set("free", stmt -> stmt.bind(free))) {
            this.free = free;
        }
    }

    public void trial(boolean trial) {
        if (set("trial", stmt -> stmt.bind(trial))) {
            this.trial = trial;
        }
    }

    private boolean set(String column, Function<Call, Call> consumer) {
        return query("""
                UPDATE
                    product
                SET %s = ?
                WHERE
                    id = ?""", column)
                .single(consumer.apply(call()).bind(id)).update()
                .changed();
    }

    public void role(Role role) {
        if (role.isPublicRole()) {
            role(0);
            return;
        }
        role(role.getIdLong());
    }

    public Mailings mailings() {
        return mailings;
    }

    public Optional<License> createLicense(String identifier) {
        return products().licenseGuild().licenses().create(this, identifier);
    }
}
