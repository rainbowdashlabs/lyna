#!/usr/bin/env node
/**
 * Convention linter for the Ember frontend.
 *
 * Checks:
 *  1. No raw <button> outside src/components/button/
 *  2. No raw <input>/<select>/<textarea> outside src/components/input/
 *  3. No raw <h1>/<h2>/<h3> outside src/components/typography/
 *  4. .vue files in src/pages/ must not exceed 500 lines (error)
 *  5. .vue files > 300 lines get a warning
 *  6. Repeated element+class patterns (>5 occurrences)
 *  7. No inline toLocale date/time formatting in src/pages/ - use util/format helpers (warning)
 *  8. No size="…" on button components that do not declare a size prop (warning)
 *
 * A per-element CSS class-count cap used to sit here, warning above 6 classes. It was removed
 * once its distribution was measured: 180 of 521 findings had exactly 7 classes and only 3 had
 * 15 or more, so with Tailwind 4 and dark-mode variants it described ordinary markup rather than
 * a smell. Element bloat is still caught by the block-size and section-density gates in
 * lint-component-size.mjs, which measure structure instead of class strings.
 *
 * Exit code 1 if any errors are found.
 */

import {readFileSync} from 'fs'
import {basename, relative, sep} from 'path'
import {SRC, walk, rel, isInsideDir, isInsideComponents, extractTemplate, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const {error, warn} = reporter

const CAT_RAW_ELEMENTS = 'Raw element usage'
const CAT_FILE_SIZE = 'File size'
const CAT_REPEATED = 'Repeated patterns'
const CAT_INLINE_FORMAT = 'Inline date formatting'
const CAT_DEAD_PROP = 'Dead prop'

/**
 * `toLocaleDateString` and `toLocaleTimeString` are always dates, but `toLocaleString` is also how
 * a *number* is formatted for the locale - which is not this rule's business.
 *
 * The two are told apart by argument count rather than by the option names, because a chart axis
 * routinely opens the options object at the end of the line and continues on the next one, which
 * a line-based check cannot read. Number formatting passes a locale and nothing else; date
 * formatting always passes options after it.
 */
const INLINE_DATE_FORMAT = /\.toLocale(Date|Time)String\(|\.toLocaleString\([^)]*,/

/** Components that are themselves a control, and so render the bare element the rule is about. */
const BARE_BUTTON_PRIMITIVES = new Set(['Modal', 'TabBar', 'TabStrip', 'ThemeSelector', 'ThemeToggle'])

const vueFiles = walk(SRC, '.vue')

const SIZE_AWARE_BUTTONS = new Set(vueFiles
    .filter(file => isInsideDir(file, 'button'))
    .filter(file => /defineProps<\{[^}]*\bsize\s*\??:/s.test(readFileSync(file, 'utf-8')))
    .map(file => basename(file, '.vue')))

for (const file of vueFiles) {
    const content = readFileSync(file, 'utf-8')
    const lines = content.split('\n')
    const template = extractTemplate(content)
    const templateLines = template.split('\n')
    const templateStartLine = content.substring(0, content.indexOf('<template>')).split('\n').length

    // ── Rule 1: No raw <button> outside components/button/ and components/input/ ──
    //
    // A handful of primitives elsewhere are an affordance in their own right - a tab strip, a theme
    // toggle, a modal's close control - and carry the styling that says so. Wrapping them in a
    // button component would layer a second set of paddings and states over their own, so they are
    // named here rather than bent into one.
    if (!isInsideDir(file, 'button') && !isInsideDir(file, 'input')) {
        for (let i = 0; i < templateLines.length; i++) {
            const line = templateLines[i]
            if (BARE_BUTTON_PRIMITIVES.has(basename(file, '.vue'))) break
            if (/<button(?=[\s/>]|$)/i.test(line) && !line.trim().startsWith('<!--')) {
                error(file, templateStartLine + i, `Raw <button> usage. Use a styled button component (PrimaryButton, SecondaryButton, IconButton, etc.)`, CAT_RAW_ELEMENTS)
            }
        }
    }

    // ── Rule 2: No raw <input>/<select>/<textarea> outside components/input/ ──
    if (!isInsideDir(file, 'input')) {
        for (let i = 0; i < templateLines.length; i++) {
            const line = templateLines[i]
            if (line.trim().startsWith('<!--')) continue
            if (/<input(?=[\s/>]|$)/i.test(line) && !/type\s*=\s*["']file["']/i.test(line)) {
                error(file, templateStartLine + i, `Raw <input> usage. Use TextInput, NumberInput, DateInput, etc.`, CAT_RAW_ELEMENTS)
            }
            if (/<select(?=[\s/>]|$)/i.test(line)) {
                error(file, templateStartLine + i, `Raw <select> usage. Use SelectInput.`, CAT_RAW_ELEMENTS)
            }
            if (/<textarea(?=[\s/>]|$)/i.test(line)) {
                error(file, templateStartLine + i, `Raw <textarea> usage. Use TextAreaInput.`, CAT_RAW_ELEMENTS)
            }
        }
    }

    // ── Rule 3: No raw <h1>/<h2>/<h3> outside components/typography/ ──
    if (!isInsideDir(file, 'typography')) {
        for (let i = 0; i < templateLines.length; i++) {
            const line = templateLines[i]
            if (line.trim().startsWith('<!--')) continue
            if (/<h[123][\s>]/i.test(line)) {
                error(file, templateStartLine + i, `Raw <${line.match(/<(h[123])/i)?.[1]}> usage. Consider using PageHeader, SectionHeader, or SubHeader.`, CAT_RAW_ELEMENTS)
            }
        }
    }

    // ── Rule 3b: No <span> with rounded-full + px- (text badges) - use Badge components instead ──
    // Only flags spans that look like text badges (have padding like px-2), not simple color dots
    if (!isInsideDir(file, 'badge')) {
        for (let i = 0; i < templateLines.length; i++) {
            const line = templateLines[i]
            if (/<span\b[^>]*\brounded-full\b[^>]*\bpx-/.test(line) || /<span\b[^>]*\bpx-[^>]*\brounded-full\b/.test(line)) {
                error(file, templateStartLine + i, `<span> with rounded-full + padding - use a Badge component (PrimaryBadge, SuccessBadge, etc.) instead.`, CAT_RAW_ELEMENTS)
            }
        }
    }

    // ── Rule 5 & 6: File size limits ──
    const isView = relative(SRC, file).startsWith(`pages${sep}`)
    const lineCount = lines.length

    if (isView) {
        for (let i = 0; i < lines.length; i++) {
            if (INLINE_DATE_FORMAT.test(lines[i])) {
                warn(file, i + 1, `Inline toLocale date formatting - use the helpers in util/format.ts.`, CAT_INLINE_FORMAT)
            }
        }
    }

    if (isView && lineCount > 500) {
        error(file, 0, `Page has ${lineCount} lines (max 500). Split into smaller components.`, CAT_FILE_SIZE)
    } else if (lineCount > 300) {
        warn(file, 0, `Component has ${lineCount} lines. Consider splitting.`, CAT_FILE_SIZE)
    }
    // ── Rule 6b: No inline object type literals in ref<> - use named types ──
    const scriptContent = content.substring(0, content.indexOf('<template>') >= 0 ? content.indexOf('<template>') : content.length)
    const scriptLines = scriptContent.split('\n')
    for (let i = 0; i < scriptLines.length; i++) {
        const line = scriptLines[i]
        if (/ref<\{[^}]+\}/.test(line) && !line.includes('Record<')) {
            warn(file, i + 1, `Inline object type in ref<> - use a named interface/type instead.`, 'Inline type')
        }
    }
}

for (const file of vueFiles) {
    const content = readFileSync(file, 'utf-8')
    const template = extractTemplate(content)
    const templateStartLine = content.substring(0, content.indexOf('<template>')).split('\n').length

    for (const match of template.matchAll(/<(\w*Button)\b[^>]*?>/gs)) {
        if (SIZE_AWARE_BUTTONS.has(match[1])) continue
        if (!/(?<![\w.:@-])size="/.test(match[0])) continue
        const line = templateStartLine + template.substring(0, match.index).split('\n').length - 1
        warn(file, line, `<${match[1]}> does not declare a size prop - size="…" silently becomes a dead DOM attribute. Use the compact prop.`, CAT_DEAD_PROP)
    }
}

// ── Rule 7: Code repetition ──────────────────────────────────────────

const patternCounts = new Map()

for (const file of vueFiles) {
    if (isInsideComponents(file)) continue
    const content = readFileSync(file, 'utf-8')
    const template = extractTemplate(content)
    const templateLines = template.split('\n')
    const templateStartLine = content.substring(0, content.indexOf('<template>')).split('\n').length

    for (let i = 0; i < templateLines.length; i++) {
        const line = templateLines[i].trim()
        if (line.startsWith('<!--')) continue

        const match = line.match(/^<(\w[\w-]*)(?:\s[^>]*)?\s+class="([^"]{15,})"/)
        if (!match) continue

        const [, tag, classes] = match
        const classList = classes.trim().split(/\s+/).filter(c => c.length > 0)
        if (classList.length < 3) continue

        // Skip pure layout utilities (flex/grid + spacing + text sizing)
        const layoutOnly = classList.every(c =>
            /^(flex|inline-flex|grid|gap-|items-|justify-|self-|place-|col-span|row-span|sm:|md:|lg:|xl:)/.test(c)
            || /^(space-[xy]-|order-|grow|shrink|basis-)/.test(c)
            || /^(text-xs|text-sm|text-base|text-lg|text-xl|text-center|text-right|text-left)$/.test(c)
            || /^(m[trblxy]?-|p[trblxy]?-|w-|h-|min-w-|max-w-|overflow-)/.test(c)
            || /^(flex-wrap|flex-col|flex-row|flex-1|relative|absolute)$/.test(c)
        )
        if (layoutOnly) continue

        const key = `<${tag} class="${classList.sort().join(' ')}">`

        if (!patternCounts.has(key)) patternCounts.set(key, [])
        patternCounts.get(key).push({file, line: templateStartLine + i})
    }
}

const repeatedPatterns = [...patternCounts.entries()]
    .filter(([, locs]) => locs.length > 5)
    .sort((a, b) => a[0].localeCompare(b[0]))

for (const [pattern, locations] of repeatedPatterns) {
    const fileCount = new Set(locations.map(l => rel(l.file))).size
    const msg = `Repeated pattern (${locations.length}x across ${fileCount} files): ${pattern} - extract to a component.`
    if (locations.length >= 10) {
        error(null, 0, msg, CAT_REPEATED)
    } else {
        warn(null, 0, msg, CAT_REPEATED)
    }
}

// ── Output ───────────────────────────────────────────────────────────

reporter.print()
reporter.exit()
