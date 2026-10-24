/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.updater.entity.ProductKeyType;
import de.chojo.lyna.feature.updater.repository.ProductKeyTypeRepository;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Outcome;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Refusal;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * The kinds of server key a product hands out, as a guild's admin sets them up.
 *
 * <p>Every key type gets a gateway secret of its own when it is made. The secret is what the product's
 * gateway authenticates with, and it opens keys of this type only; replacing it shuts out whoever held
 * the old one.
 */
@Singleton
public class ProductKeyTypeService {
    /** What every gateway secret starts with, so a leaked one says what it is. */
    public static final String SECRET_PREFIX = "lyg_";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProductKeyTypeRepository keyTypes;

    @Inject
    public ProductKeyTypeService(ProductKeyTypeRepository keyTypes) {
        this.keyTypes = keyTypes;
    }

    /**
     * @return the product's key types, by name
     */
    public List<ProductKeyType> forProduct(int productId) {
        return keyTypes.forProduct(productId);
    }

    /**
     * Refused as {@link Refusal#INVALID_REQUEST} for a draft out of bounds and as {@link Refusal#PENDING}
     * when the product already has a type by that slug or is not the guild's.
     */
    public Outcome<ProductKeyType> create(long guildId, int productId, ProductKeyType.Draft draft) {
        if (draft == null || !draft.valid()) return Outcome.refused(Refusal.INVALID_REQUEST);
        return Outcome.of(keyTypes.create(guildId, productId, draft.cleaned(), secret()), Refusal.PENDING);
    }

    /**
     * Refused as {@link Refusal#INVALID_REQUEST} for a draft out of bounds and as {@link Refusal#NOT_FOUND}
     * when there is no such type on the guild's products or its new slug is taken.
     */
    public Outcome<ProductKeyType> update(long guildId, int id, ProductKeyType.Draft draft) {
        if (draft == null || !draft.valid()) return Outcome.refused(Refusal.INVALID_REQUEST);
        return Outcome.of(keyTypes.update(guildId, id, draft.cleaned()), Refusal.NOT_FOUND);
    }

    /**
     * Gives a key type a new gateway secret; the old one stops working at once.
     */
    public Outcome<ProductKeyType> replaceSecret(long guildId, int id) {
        return Outcome.of(keyTypes.replaceSecret(guildId, id, secret()), Refusal.NOT_FOUND);
    }

    /**
     * Ends a key type and every key of it.
     *
     * @return whether there was such a type on the guild's products
     */
    public boolean delete(long guildId, int id) {
        return keyTypes.delete(guildId, id);
    }

    /**
     * @return a fresh gateway secret: its prefix and 32 random bytes
     */
    static String secret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
