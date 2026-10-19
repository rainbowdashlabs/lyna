/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.mail.entity;

/**
 * One of the links a guild's purchase mail closes with.
 *
 * @param label what the link reads as
 * @param url   where it goes, always http or https
 */
public record MailLink(String label, String url) {}
