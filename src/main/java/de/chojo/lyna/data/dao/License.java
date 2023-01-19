package de.chojo.lyna.data.dao;

import de.chojo.lyna.data.dao.platforms.Platform;
import de.chojo.lyna.data.dao.products.Product;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class License {
    /**
     * The product the license is for.
     */
    Product product;
    /**
     * Information about the platform and license.
     */
    Platform platform;
    /**
     * Identifier of the user on the platform.
     */
    String userIdentifier;
    /**
     * License id
     */
    int id;
    /**
     * License key
     */
    String key;
    /**
     * The owner owning this license.
     */
    @Nullable
    LicenseUser owner;
    /**
     * The sub users of the license.
     */
    List<LicenseUser> subs;
}
