/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.repository.AccountRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * What an account is called.
 *
 * <p>A name is either the provider's or the account's own. Discord guarantees its handles are
 * unique, so a linked account carries the handle and nothing else; an account naming itself gets
 * four digits, allocated here rather than asked for, so two people may both be "ada" without either
 * finding out what the other picked.
 */
@Singleton
public class UsernameService {

    /**
     * What somebody may call themselves, before the digits are added.
     *
     * <p>Letters, digits, and the three separators people expect in a handle. No spaces and no
     * punctuation that could make one name read as another.
     */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9._-]{1,30})[A-Za-z0-9]$");

    private static final int DISCRIMINATOR_ATTEMPTS = 12;

    private final AccountRepository accounts;

    @Inject
    public UsernameService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * Gives an account a name of its own choosing.
     *
     * <p>Refused while a Discord identity is linked: that name is Discord's to change, and letting
     * both sides write it would mean the next sign-in quietly undid whatever was typed here.
     *
     * @param accountId the account to name
     * @param username  the name, without digits
     * @return the discriminator it was given
     * @throws IllegalArgumentException if the name is not one somebody may take
     * @throws IllegalStateException    if the account is linked, or the name has no free digits left
     */
    public String setUsername(int accountId, String username) {
        String trimmed = username == null ? "" : username.trim();
        if (!USERNAME.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "A username is 3 to 32 characters of letters, digits, dots, dashes or underscores");
        }
        if (accounts.findIdentity(accountId, AccountIdentity.DISCORD).isPresent()) {
            throw new IllegalStateException("This account is named by Discord. Unlink it to choose a name.");
        }
        return allocate(accountId, trimmed)
                .orElseThrow(() ->
                        new IllegalStateException("Every discriminator for that username is taken. Pick another."));
    }

    /**
     * Takes the account's name from the provider that owns it.
     *
     * <p>The digits go: a handle is unique where it comes from, and keeping stale digits beside it
     * would show a name that exists nowhere.
     */
    public void syncFromHandle(int accountId, String handle) {
        if (handle == null || handle.isBlank()) return;
        accounts.writeProvidedUsername(accountId, handle.trim());
    }

    /**
     * Gives an account's name digits of its own, for when the provider that guaranteed it was unique
     * is no longer linked.
     *
     * <p>The name itself is left alone. Somebody who has been "ada" to their sharees stays "ada", and
     * only gains the digits that keep them apart from the next one.
     */
    public void detach(int accountId) {
        Optional<Account> account = accounts.findById(accountId);
        if (account.isEmpty()) return;
        String username = account.get().username();
        if (username == null || username.isBlank() || account.get().discriminator() != null) return;
        allocate(accountId, username);
    }

    /**
     * Tries at random first, because that is what keeps two people picking the same name from
     * learning anything about each other. Only a name that is nearly full falls back to walking the
     * free digits, which is the rare case and the one where guessing would not terminate.
     *
     * @return the digits the name was given, or nothing when every one of them is taken
     */
    private Optional<String> allocate(int accountId, String username) {
        for (int attempt = 0; attempt < DISCRIMINATOR_ATTEMPTS; attempt++) {
            String discriminator = "%04d".formatted(ThreadLocalRandom.current().nextInt(1, 10_000));
            if (accounts.writeUsername(accountId, username, discriminator)) return Optional.of(discriminator);
        }
        for (String discriminator : free(username)) {
            if (accounts.writeUsername(accountId, username, discriminator)) return Optional.of(discriminator);
        }
        return Optional.empty();
    }

    /**
     * @return the digits nobody holds for that name, so a name that is nearly full still resolves
     */
    private List<String> free(String username) {
        Set<String> taken = accounts.takenDiscriminators(username);
        List<String> free = new ArrayList<>();
        for (int candidate = 1; candidate < 10_000; candidate++) {
            String discriminator = "%04d".formatted(candidate);
            if (!taken.contains(discriminator)) free.add(discriminator);
        }
        return free;
    }
}
