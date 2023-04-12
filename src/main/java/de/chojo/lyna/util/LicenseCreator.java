package de.chojo.lyna.util;

import de.chojo.lyna.data.dao.products.Product;

import java.util.Random;

public class LicenseCreator {
    private static final String CHARS = "ABCDEFGHIJKLMNPQRSTUVWXYZ123456789";

    public static String create(long seed, Product product, String identifier) {
        long guildId = product.guildId();
        var prod = generateRandomSequence((seed << 12) + product.id() + guildId + 13, 8);
        var plat = generateRandomSequence((seed << 16) + identifier.hashCode() + guildId + 17, 6);
        var user = generateRandomSequence((seed << 24) + identifier.hashCode() + guildId + 23, 12);
        var check = generateRandomSequence((seed << 20) + (product.id() + 31) + (identifier.hashCode() + 41) + guildId, 4);

        return "%s-%s-%s-%s".formatted(prod, plat, user, check);
    }

    public static String generateRandomSequence(long seed, int length) {
        Random rand = new Random(seed);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(CHARS.charAt(rand.nextInt(CHARS.length())));
        }
        return builder.toString();
    }
    public static String generateRandomSequence(int length) {
        Random rand = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(CHARS.charAt(rand.nextInt(CHARS.length())));
        }
        return builder.toString();
    }
}
