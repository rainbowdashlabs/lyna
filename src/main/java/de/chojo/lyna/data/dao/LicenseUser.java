package de.chojo.lyna.data.dao;

import net.dv8tion.jda.api.entities.Member;

import java.util.List;

public class LicenseUser {
    long memberId;
    /**
     * The member attached to this user
     */
    Member member;
    /**
     * The licenses owned by this user.
     */
    List<License> licenses;
}
