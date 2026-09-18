/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import DOMPurify from 'dompurify'
import {marked} from 'marked'

/**
 * The one way markdown becomes HTML in this application.
 *
 * `marked` dropped its own sanitising in version 5 and passes raw HTML through by design, so the
 * caller has to clean what it produces. The markdown here is a product description written by an
 * operator and shown to anybody who opens the storefront, so an unsanitised path is stored
 * cross-site scripting against every visitor.
 *
 * A server render has no DOM to clean with. There the markdown is escaped instead of parsed:
 * losing the formatting is visible and recoverable, letting unsanitised HTML into the response is
 * neither. The browser renders it properly as soon as it takes over.
 */
export function renderMarkdown(markdown: string | null | undefined): string {
    if (!markdown) return ''
    if (typeof window === 'undefined') return escape(markdown)
    try {
        return DOMPurify.sanitize(marked.parse(markdown, {async: false}))
    } catch {
        return escape(markdown)
    }
}

/** Text a browser shows verbatim, for the paths that have no DOM to sanitise with. */
function escape(text: string): string {
    return text
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;')
}
