/**
 * Shared utilities for frontend linters.
 */

import {readdirSync, readFileSync} from 'fs'
import {join, relative, sep} from 'path'

// ── Paths ───────────────────────────────────────────────────────────

export const SRC = new URL('../src', import.meta.url).pathname
export const PAGES_DIR = join(SRC, 'pages')

// ── Colors ──────────────────────────────────────────────────────────

export const RED = '\x1b[31m'
export const GREEN = '\x1b[32m'
export const YELLOW = '\x1b[33m'
export const RESET = '\x1b[0m'
export const BOLD = '\x1b[1m'

// ── File helpers ────────────────────────────────────────────────────

export function walk(dir, ext) {
    const results = []
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) {
            results.push(...walk(full, ext))
        } else if (entry.name.endsWith(ext)) {
            results.push(full)
        }
    }
    return results
}

export function rel(file) {
    return relative(join(SRC, '..'), file)
}

export function isInsideDir(file, dirSegment) {
    const r = relative(SRC, file)
    return r.startsWith(`components${sep}${dirSegment}${sep}`)
}

export function isInsideComponents(file) {
    const r = relative(SRC, file)
    return r.startsWith(`components${sep}`)
}

export function extractTemplate(content) {
    const start = content.indexOf('<template>')
    const end = content.lastIndexOf('</template>')
    if (start === -1 || end === -1) return ''
    return content.substring(start, end + '</template>'.length)
}

// ── Route parsing (Nuxt file-based routing) ─────────────────────────

const SECTION_ROOTS = ['admin/g/:guildId', 'admin/instance', 'admin', 'account']

function pagePathToRoutePath(file) {
    let p = relative(PAGES_DIR, file).split(sep).join('/').replace(/\.vue$/, '')
    if (p.endsWith('/index')) p = p.slice(0, -'/index'.length)
    if (p === 'index') p = ''
    return p
        .split('/')
        .map(seg => {
            if (seg.startsWith('[...') && seg.endsWith(']')) return ':pathMatch(.*)*'
            if (seg.startsWith('[[') && seg.endsWith(']]')) return `:${seg.slice(2, -2)}?`
            if (seg.startsWith('[') && seg.endsWith(']')) return `:${seg.slice(1, -1)}`
            return seg
        })
        .join('/')
}

function stripSectionRoot(path) {
    for (const root of SECTION_ROOTS) {
        if (path === root) return ''
        if (path.startsWith(`${root}/`)) return path.slice(root.length + 1)
    }
    return path
}

/**
 * Parse all named routes from the Nuxt page files in src/pages.
 * Returns array of {name, path, component} objects, where path is relative
 * to the page's section root (account/, admin/, admin/g/:guildId, admin/instance)
 * so that routes under different sections stay comparable, and component is the
 * page's view import normalized to a `@/views/...` spec when the page delegates
 * its markup to one.
 */
export function parseRoutes() {
    const routes = []
    for (const file of walk(PAGES_DIR, '.vue')) {
        const content = readFileSync(file, 'utf-8')
        const metaBlock = content.match(/definePageMeta\(\{([\s\S]*?)\}\)/)
        const nameMatch = metaBlock && metaBlock[1].match(/name:\s*'([^']+)'/)
        if (!nameMatch) continue
        // A page that only forwards to another address shows nobody anything, so there is nothing
        // about it to document and it is not a gap when no help page names it.
        if (/redirect:\s*'/.test(metaBlock[1])) continue
        const compMatch = content.match(/import\s+\w+\s+from\s+'[~@]\/(views\/[^']+)'/)
        routes.push({
            name: nameMatch[1],
            path: stripSectionRoot(pagePathToRoutePath(file)),
            component: compMatch ? `@/${compMatch[1]}` : null,
        })
    }
    return routes
}

/**
 * Normalize a route path for comparison.
 * Replaces all :paramName and :paramName? with :id.
 */
export function normalizePath(path) {
    return path
        .replace(/:[a-zA-Z]+\??/g, ':id')
        .replace(/^\/|\/$/g, '')
}

// ── Reporting ───────────────────────────────────────────────────────

/**
 * Creates an error/warning collector with formatted output.
 */
export function createReporter() {
    const errors = []
    const warnings = []

    return {
        error(file, line, msg, category = '') {
            errors.push({file: typeof file === 'string' && file.startsWith('/') ? rel(file) : file, line, msg, category})
        },
        warn(file, line, msg, category = '') {
            warnings.push({file: typeof file === 'string' && file.startsWith('/') ? rel(file) : file, line, msg, category})
        },
        get errors() { return errors },
        get warnings() { return warnings },

        print() {
            const printGroup = (items, color, label) => {
                if (items.length === 0) return
                console.log(`\n${color}${BOLD}${label} (${items.length}):${RESET}`)
                // Group by category
                const groups = new Map()
                for (const item of items) {
                    const cat = item.category || 'Other'
                    if (!groups.has(cat)) groups.set(cat, [])
                    groups.get(cat).push(item)
                }
                for (const [cat, entries] of groups) {
                    if (groups.size > 1) console.log(`\n  ${BOLD}${cat}${RESET}`)
                    for (const e of entries) {
                        if (e.file) {
                            const loc = e.line > 0 ? `:${e.line}` : ''
                            console.log(`    ${color}${label.toLowerCase().slice(0, -1)}${RESET} ${e.file}${loc}: ${e.msg}`)
                        } else {
                            console.log(`    ${color}${label.toLowerCase().slice(0, -1)}${RESET} ${e.msg}`)
                        }
                    }
                }
            }
            printGroup(warnings, YELLOW, 'Warnings')
            printGroup(errors, RED, 'Errors')
        },

        exit() {
            if (errors.length > 0) {
                console.log(`\n${RED}Lint failed with ${errors.length} error(s).${RESET}\n`)
                process.exit(1)
            } else if (warnings.length > 0) {
                console.log(`\n${YELLOW}Lint passed with ${warnings.length} warning(s).${RESET}\n`)
            } else {
                console.log(`\nLint passed.\n`)
            }
        }
    }
}
