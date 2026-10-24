/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.entity;

/**
 * BuiltByBit asking for the licence key to write into a download.
 *
 * @param memberId      who is downloading
 * @param resourceId    the resource downloaded
 * @param versionNumber the version downloaded, as BuiltByBit names it
 */
public record BuiltByBitKeyRequest(long memberId, long resourceId, String versionNumber) {}
