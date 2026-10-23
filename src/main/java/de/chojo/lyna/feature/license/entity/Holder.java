/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.license.entity;

/**
 * The account holding a licence.
 *
 * @param accountId the account
 * @param discordId the holder's Discord id, or null for an account that never linked Discord
 * @param name      what to call them: their username, else an address of theirs, else the account
 */
public record Holder(int accountId, Long discordId, String name) {}
