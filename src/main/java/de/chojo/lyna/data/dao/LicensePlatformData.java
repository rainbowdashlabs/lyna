package de.chojo.lyna.data.dao;

import de.chojo.lyna.data.dao.platforms.Platform;

public class LicensePlatformData {
    /**
     * The platform the license is redeemed on.
     */
    Platform platform;
    /**
     * Identifier of the user on the platform.
     */
    String platformIdentifier;
}
