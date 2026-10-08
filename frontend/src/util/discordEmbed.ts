/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Discord's component embed for a link preview: a Container of components, read from the page's
 * head (docs.discord.com/developers/link-previews/component-embeds). Discord ignores a payload over
 * 3,000 bytes, so the longest free text is cut until it fits.
 */
export const DISCORD_EMBED_LIMIT = 3000

export interface EmbedButton {
    label: string
    url: string
}

export interface EmbedCard {
    /** Discord markdown; the first line is the heading. */
    heading: string
    /** The text that is cut first when the payload is too large. */
    body: string
    thumbnail: string | null
    buttons: EmbedButton[]
}

function bytes(text: string): number {
    return new TextEncoder().encode(text).length
}

function payload(card: EmbedCard, body: string): string {
    const text = {type: 10, content: body ? `${card.heading}\n${body}` : card.heading}
    const head = card.thumbnail
        ? {type: 9, components: [text], accessory: {type: 11, media: {url: card.thumbnail}}}
        : text
    const components: object[] = [head]
    if (card.buttons.length) {
        components.push({type: 14})
        components.push({
            type: 1,
            components: card.buttons.slice(0, 5).map(button => ({type: 2, style: 5, label: button.label, url: button.url})),
        })
    }
    return JSON.stringify({component: {type: 17, components}})
}

/** Closes a code block a cut left open, so the rest of the embed is not swallowed by it. */
function closeFences(markdown: string): string {
    return (markdown.match(/^```/gm) ?? []).length % 2 === 1 ? `${markdown}\n\`\`\`` : markdown
}

/**
 * The body cut back by whole lines, so a cut never lands inside a word, a link or a code fence.
 */
function shorten(body: string): string {
    const lines = body.split('\n')
    if (lines.length <= 1) return body.length > 40 ? `${body.slice(0, Math.floor(body.length * 0.8)).trimEnd()}…` : ''
    return closeFences(`${lines.slice(0, Math.max(1, Math.floor(lines.length * 0.8))).join('\n').replace(/\n```$/, '').trimEnd()}\n…`)
}

/**
 * The embed as JSON ready for a script element: within Discord's limit, and with every {@code <}
 * escaped so the text cannot close the element it sits in.
 */
export function discordEmbed(card: EmbedCard): string {
    let body = card.body
    let json = payload(card, body)
    while (bytes(json) > DISCORD_EMBED_LIMIT && body.length > 0) {
        body = shorten(body)
        json = payload(card, body)
    }
    return json.replace(/</g, '\\u003c')
}

/**
 * A product's markdown as Discord renders it. Discord knows headings down to three levels, emphasis,
 * lists, quotes, links and code; images, HTML and tables it cannot show, so those go, and relative
 * links are made absolute against the page they came from.
 */
export function discordMarkdown(markdown: string | null | undefined, base: string): string {
    if (!markdown) return ''
    const absolute = (url: string) => /^[a-z]+:/i.test(url) || url.startsWith('#') ? url : new URL(url, base).href
    return closeFences(markdown
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/<[^>]+>/g, '')
        .replace(/!\[[^\]]*]\([^)]*\)/g, '')
        .replace(/^\s*\|.*\|\s*$/gm, '')
        .replace(/^#{4,}\s+/gm, '### ')
        .replace(/\[([^\]]+)]\(([^)\s]+)[^)]*\)/g, (_, text: string, url: string) => `[${text}](${absolute(url)})`)
        .replace(/\n{3,}/g, '\n\n')
        .trim())
}

/**
 * The opening of a description as Discord markdown: its first paragraph of prose, cut at a word near
 * {@code limit}. Headings, lists, code and every image - badges included, linked or not - are left
 * out, since an embed shows none of them well and a preview only needs to say what the plugin is.
 */
export function discordAbstract(markdown: string | null | undefined, base: string, limit = 350): string {
    const prose = discordMarkdown(markdown
        ?.replace(/\[!\[[^\]]*]\([^)]*\)]\([^)]*\)/g, '')
        .replace(/<a\b[^>]*>\s*<img\b[^>]*>\s*<\/a>/gi, ''), base)
        .replace(/```[\s\S]*?```/g, '')
        .replace(/\[]\([^)]*\)/g, '')
        .split(/\n\s*\n/)
        .map(block => block.trim())
        .find(block => block.length > 0 && !/^(#|[-*+]\s|\d+\.\s|>|\|)/.test(block))
        ?.replace(/\s*\n\s*/g, ' ') ?? ''
    if (prose.length <= limit) return prose
    const cut = prose.slice(0, limit)
    return `${cut.slice(0, Math.max(cut.lastIndexOf(' '), limit / 2)).trimEnd()}…`
}

/** Markdown reduced to plain text, for the places that show no formatting, such as OpenGraph. */
export function plainText(markdown: string | null | undefined, limit = 300): string {
    if (!markdown) return ''
    const text = markdown
        .replace(/```[\s\S]*?```/g, ' ')
        .replace(/<[^>]+>/g, ' ')
        .replace(/!\[[^\]]*]\([^)]*\)/g, ' ')
        .replace(/\[([^\]]*)]\([^)]*\)/g, '$1')
        .replace(/^#+\s*/gm, '')
        .replace(/^\s*(?:[-*+]|\d+\.)\s+/gm, '')
        .replace(/[*_`>~|]/g, '')
        .replace(/\s+/g, ' ')
        .trim()
    return text.length > limit ? `${text.slice(0, limit - 1).trimEnd()}…` : text
}
