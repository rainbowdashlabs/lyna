/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MailBlock, MailingTemplate} from '~/api/admin'

/**
 * Which mail a product sends, as the key of the words for it: the guild's default, its own, or HTML
 * written before the editor existed.
 */
export function mailingStateKey(template: MailingTemplate): string {
    if (template.usesDefault) return 'page.admin.g.guildId.mailing.usesDefault'
    if (!template.blocks) return 'page.admin.g.guildId.mailing.middotWrittenBeforeTheEditor'
    return 'page.admin.g.guildId.mailing.ownMail'
}

/**
 * A stored block document, or no blocks when there is none or it does not parse.
 */
export function parseMailBlocks(blocks: string | null): MailBlock[] {
    if (!blocks) return []
    try {
        return JSON.parse(blocks) as MailBlock[]
    } catch {
        return []
    }
}
