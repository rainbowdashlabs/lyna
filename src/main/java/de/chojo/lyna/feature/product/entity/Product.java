/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.product.entity;

import de.chojo.lyna.data.dao.products.Products;
import de.chojo.lyna.data.dao.products.downloads.Downloads;
import de.chojo.lyna.data.dao.products.mailings.Mailings;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.entity.LicenseSource;
import de.chojo.lyna.feature.product.repository.ProductRepository;
import de.chojo.lyna.util.Version;
import de.chojo.nexus.NexusRest;
import de.chojo.nexus.entities.PageComponentXO;
import de.chojo.nexus.requests.v1.search.Direction;
import de.chojo.nexus.requests.v1.search.Sort;
import de.chojo.sadu.queries.api.call.Call;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class Product {
    private static final ProductRepository REPOSITORY = new ProductRepository();

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
        return REPOSITORY.delete(id, products.guildId());
    }

    public long guildId() {
        return products.guildId();
    }

    public Products products() {
        return products;
    }

    public Optional<Role> role(Guild guild) {
        return Optional.ofNullable(guild.getRoleById(role));
    }

    public Downloads downloads() {
        return downloads;
    }

    public NexusRest nexus() {
        return nexus;
    }

    public List<License> license(Member member) {
        return REPOSITORY.licenseIdsFor(guildId(), id, member.getIdLong()).stream()
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
            PageComponentXO complete = products()
                    .nexus()
                    .v1()
                    .search()
                    .search()
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
        return REPOSITORY.set(id, column, consumer);
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

    public Optional<License> createLicense(String identifier, LicenseSource source) {
        return products().licenseGuild().licenses().create(this, identifier, source);
    }
}
