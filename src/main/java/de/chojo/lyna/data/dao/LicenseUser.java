package de.chojo.lyna.data.dao;

import net.dv8tion.jda.api.entities.Member;

import java.util.List;

public class LicenseUser {
    long id;
    /**
     * The member attached to this user
     */
    Member member;
    /**
     * The licenses owned by this user.
     */
    List<License> licenses;


    public long id() {
        return id;
    }

    public Member member() {
        return member;
    }

    public List<License> licenses() {
        return licenses;
    }
}
