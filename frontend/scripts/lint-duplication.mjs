#!/usr/bin/env node
/**
 * Code repetition linter for the Ember frontend.
 *
 * Walks every .vue and .ts file under src/ and looks for blocks of lines whose
 * normalised shape repeats across at least --min-files=M (default 2) different
 * files. Within a single file the same shape is only counted once so legitimate
 * per-iteration repeats don't drown the report.
 *
 * A single-file scan is split into single-purpose regions first. Vue SFCs are
 * cut along their top-level <template>, <script> and <style> blocks; a .ts file
 * is one big script region. Windows never straddle a region boundary, and
 * <style> regions are skipped entirely: after normalisation every CSS
 * declaration looks like every other one, so any two rules with the same number
 * of declarations collide.
 *
 * Script and template regions are scanned with different window sizes and
 * different normalisations, because markup and logic carry information at very
 * different densities:
 *
 *   - Script windows are --window=N lines (default 10). Normalisation collapses
 *     string literals to "", numbers to 0 and identifiers of three characters or
 *     more to a single sigil. That keeps copy-pasted logic recognisable across
 *     files that renamed variables, but it also erases most of what a line says,
 *     which is why the window has to be long before a match means anything.
 *   - Template windows are --template-window=N lines (default 12) and keep tag
 *     names, attribute names and directive names intact - only attribute values
 *     and interpolations are collapsed. Repeated markup is the thing this
 *     cleanup is actually hunting for, so the fingerprint stays sharp and the
 *     match is about structure rather than about wording.
 *
 * Three filters keep structurally-trivial windows out of the report. They apply
 * to both passes:
 *
 *   - A window must contain at least ceil(window / 2) *distinct* normalised
 *     lines. Eight consecutive `key: "",` lines carry no information no matter
 *     how many files they appear in.
 *   - No single normalised line may account for --max-line-share (default 0.4)
 *     or more of the window, which catches the same shape from the other side:
 *     a run of identical property lines, identical table headers or identical
 *     closing tags is padding, not duplication.
 *   - Template windows additionally need at least four lines that open an
 *     element. A block that is nothing but attributes is a prop-forwarding list
 *     between a parent and its own child component, not markup worth extracting.
 *
 * Finally, matches are clustered. Overlapping windows over the same duplicated
 * region used to be reported once per line offset, so a single copy-pasted
 * function surfaced as six near-identical warnings; consecutive windows that
 * shift in lockstep across the same set of files are now merged into one
 * finding that names the full line range, and a finding whose range is already
 * covered by a larger one is dropped.
 *
 * Tunables (CLI flags or env):
 *   --window=N           script window in lines (default 10, env LINT_DUP_WINDOW)
 *   --template-window=N  template window in lines (default 12)
 *   --min-files=M        only report when reused in >= M files (default 2)
 *   --warn=N             warn for blocks with >= N occurrences (default 2)
 *   --error=N            error for blocks with >= N occurrences (default 4)
 *   --max-line-share=F   reject windows dominated by one line (default 0.4)
 *
 * The check exempts files inside src/components/ - extracting *into* components
 * is the goal, so reuse showing up in component libraries is fine and expected.
 */

import {readFileSync} from 'fs'
import {SRC, walk, rel, isInsideComponents, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const {warn, error} = reporter
const CATEGORY = 'Code repetition'

const args = new Map()
for (const arg of process.argv.slice(2)) {
    const m = arg.match(/^--([\w-]+)=(.+)$/)
    if (m) args.set(m[1], m[2])
}

const SCRIPT_WINDOW = Number(args.get('window') ?? process.env.LINT_DUP_WINDOW ?? 10)
const TEMPLATE_WINDOW = Number(args.get('template-window') ?? process.env.LINT_DUP_TEMPLATE_WINDOW ?? 12)
const MIN_FILES = Number(args.get('min-files') ?? process.env.LINT_DUP_MIN_FILES ?? 2)
const WARN_AT = Number(args.get('warn') ?? process.env.LINT_DUP_WARN ?? 2)
const ERROR_AT = Number(args.get('error') ?? process.env.LINT_DUP_ERROR ?? 4)
const MAX_LINE_SHARE = Number(args.get('max-line-share') ?? process.env.LINT_DUP_MAX_LINE_SHARE ?? 0.4)
const MIN_TEMPLATE_ELEMENTS = 4

const BOILERPLATE_LINE = new RegExp([
    '^\\s*(',
    'import\\b|export\\b|from\\b',
    '|\\}|\\{|<\\/?[\\w-]+',
    '|\\/\\/|\\/\\*|\\*|<!--|--',
    '|const\\s+\\{?[\\w,\\s]+\\}?\\s*=\\s*(ref|computed|reactive|shallowRef|toRef|toRefs|use[A-Z]\\w*|inject|defineModel)\\b',
    '|const\\s+\\w+\\s*=\\s*ref<',
    '|defineProps|defineEmits|defineExpose|defineSlots',
    '|onMounted|onBeforeUnmount|onBeforeMount|onUnmounted|watch\\(|watchEffect\\(',
    '|:[\\w-]+(:[\\w-]+)?\\s*=',
    '|@[\\w-]+(\\.[\\w]+)*\\s*=',
    '|v-[\\w-]+(:[\\w-]+)?\\b',
    ').*$|^.{0,15}$',
].join(''))

const TEMPLATE_FILLER_LINE = /^(<\/[\w-]+>|<[\w-]+\s*\/?>|\{\{\}\}|\/?>)$/
const ELEMENT_OPEN = /<[A-Za-z][\w-]*/

/**
 * Marks lines that sit inside a `defineProps<{ … }>()` or `defineEmits<{ … }>()` macro body.
 * The declaration shapes are part of Vue's compile-time surface area - they exist precisely to give
 * each component a typed contract, and lifting them into a shared file would defeat that purpose.
 * Standalone `interface` / `type` declarations are intentionally NOT silenced - when two files
 * declare the same shape there, it's real duplication and should be extracted into a shared type.
 */
function markMacroBodyLines(lines) {
    const inMacro = new Array(lines.length).fill(false)
    let depth = 0
    let active = false
    const opener = /\b(defineProps|defineEmits)\s*<\s*\{/
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i]
        let j = 0
        while (j < line.length) {
            if (!active) {
                const remainder = line.slice(j)
                const m = remainder.match(opener)
                if (!m) break
                const start = j + (m.index ?? 0) + m[0].length
                active = true
                depth = 1
                j = start
                continue
            }
            const ch = line[j]
            if (ch === '{') depth++
            else if (ch === '}') {
                depth--
                if (depth === 0) {
                    active = false
                    j++
                    continue
                }
            }
            j++
        }
        if (active || (depth === 0 && lines[i].match(/^\s*\}>\s*\(\s*\)/))) {
            inMacro[i] = true
        }
    }
    return inMacro
}

/**
 * Labels every line of a file with the single-purpose region it belongs to:
 * `script`, `template`, `style` or `other` for the SFC block delimiters themselves.
 * Vue puts the top-level blocks at column zero, so nested `<template #slot>` tags
 * inside markup do not confuse the split.
 */
function markRegions(lines, isVue) {
    if (!isVue) return new Array(lines.length).fill('script')
    const kinds = new Array(lines.length).fill('other')
    let current = 'other'
    for (let i = 0; i < lines.length; i++) {
        const open = lines[i].match(/^<(template|script|style)[\s>]/)
        const close = lines[i].match(/^<\/(template|script|style)>/)
        if (current === 'other' && open) {
            current = open[1]
            continue
        }
        if (close && close[1] === current) {
            current = 'other'
            continue
        }
        kinds[i] = current
    }
    return kinds
}

function scriptFingerprint(line) {
    return line
        .replace(/(['"`])(?:\\.|(?!\1)[^\\])*\1/g, '""')
        .replace(/\d+(\.\d+)?/g, '0')
        .replace(/\b[a-zA-Z_][a-zA-Z0-9_]{2,}\b/g, 'I')
        .replace(/\s+/g, ' ')
        .trim()
}

function templateFingerprint(line) {
    return line
        .replace(/=("|')(?:\\.|(?!\1)[^\\])*\1/g, '=""')
        .replace(/\{\{[^}]*\}\}/g, '{{}}')
        .replace(/\s+/g, ' ')
        .trim()
}

function isBoilerplateWindow(lines, macroFlags) {
    let trivial = 0
    for (let i = 0; i < lines.length; i++) {
        if (macroFlags && macroFlags[i]) {
            trivial++
            continue
        }
        if (BOILERPLATE_LINE.test(lines[i])) trivial++
    }
    return trivial >= Math.ceil(lines.length / 2)
}

/**
 * Share of the window taken up by its single most frequent normalised line.
 * A high share means the window is a run of one repeated shape and carries
 * no structure worth reporting.
 */
function dominantLineShare(fingerprints) {
    if (fingerprints.length === 0) return 1
    const counts = new Map()
    for (const fp of fingerprints) counts.set(fp, (counts.get(fp) ?? 0) + 1)
    return Math.max(...counts.values()) / fingerprints.length
}

function carriesInformation(fingerprints, windowSize) {
    if (new Set(fingerprints).size < Math.ceil(windowSize / 2)) return false
    return dominantLineShare(fingerprints) < MAX_LINE_SHARE
}

const files = [
    ...walk(SRC, '.vue'),
    ...walk(SRC, '.ts'),
].filter(f => {
    if (isInsideComponents(f)) return false
    if (f.endsWith('.d.ts')) return false
    if (f.includes(`${SRC}/api/`) || f.includes(`${SRC}/i18n/`)) return false
    return true
})

const buckets = new Map()

for (const file of files) {
    const lines = readFileSync(file, 'utf-8').split('\n')
    const macroFlags = markMacroBodyLines(lines)
    const kinds = markRegions(lines, file.endsWith('.vue'))
    const seen = new Set()

    const record = (kind, fingerprints, start) => {
        const fp = fingerprints.join('\n')
        if (fp.trim().length < 40) return
        const key = `${kind}\n${fp}`
        if (seen.has(key)) return
        seen.add(key)
        if (!buckets.has(key)) buckets.set(key, {kind, locations: []})
        buckets.get(key).locations.push({file, line: start + 1})
    }

    for (let i = 0; i <= lines.length - SCRIPT_WINDOW; i++) {
        if (!kinds.slice(i, i + SCRIPT_WINDOW).every(k => k === 'script')) continue
        const window = lines.slice(i, i + SCRIPT_WINDOW)
        if (isBoilerplateWindow(window, macroFlags.slice(i, i + SCRIPT_WINDOW))) continue
        const fingerprints = window.map(scriptFingerprint)
        if (!carriesInformation(fingerprints.filter(Boolean), SCRIPT_WINDOW)) continue
        record('script', fingerprints, i)
    }

    if (!file.endsWith('.vue')) continue

    for (let i = 0; i <= lines.length - TEMPLATE_WINDOW; i++) {
        if (!kinds.slice(i, i + TEMPLATE_WINDOW).every(k => k === 'template')) continue
        const fingerprints = lines.slice(i, i + TEMPLATE_WINDOW).map(templateFingerprint)
        if (fingerprints.some(fp => fp.length === 0)) continue
        if (fingerprints.filter(fp => !TEMPLATE_FILLER_LINE.test(fp)).length < Math.ceil(TEMPLATE_WINDOW / 2)) continue
        if (fingerprints.filter(fp => ELEMENT_OPEN.test(fp)).length < MIN_TEMPLATE_ELEMENTS) continue
        if (!carriesInformation(fingerprints, TEMPLATE_WINDOW)) continue
        record('template', fingerprints, i)
    }
}

const matches = []
for (const [key, {kind, locations}] of buckets) {
    if (locations.length < WARN_AT) continue
    if (new Set(locations.map(l => l.file)).size < MIN_FILES) continue
    matches.push({kind, locations, sample: key.split('\n').slice(1)})
}

/**
 * Merges consecutive windows over the same duplicated region into one finding.
 * Two matches belong together when they cover the same set of files and every
 * one of their start lines advanced by the same amount, which is exactly what
 * a sliding window over one copy-pasted block produces.
 */
function cluster(matches) {
    const groups = new Map()
    for (const m of matches) {
        const key = `${m.kind}|${m.locations.map(l => l.file).sort().join('|')}`
        if (!groups.has(key)) groups.set(key, [])
        groups.get(key).push(m)
    }
    const clusters = []
    for (const group of groups.values()) {
        const size = group[0].kind === 'template' ? TEMPLATE_WINDOW : SCRIPT_WINDOW
        group.sort((a, b) => Math.min(...a.locations.map(l => l.line)) - Math.min(...b.locations.map(l => l.line)))
        const open = []
        for (const m of group) {
            const starts = new Map(m.locations.map(l => [l.file, l.line]))
            const target = open.find(c => {
                const deltas = [...starts].map(([f, line]) => line - c.end.get(f))
                return deltas.every(d => d === deltas[0] && d >= 0 && d <= size)
            })
            if (target) {
                for (const [f, line] of starts) target.end.set(f, line)
                target.count = Math.max(target.count, m.locations.length)
                continue
            }
            open.push({
                kind: m.kind,
                size,
                start: new Map(starts),
                end: new Map(starts),
                count: m.locations.length,
                sample: m.sample,
            })
        }
        clusters.push(...open)
    }
    return clusters
}

/**
 * Drops findings whose line range is already covered by a larger finding over
 * the same files, so one duplicated region is named once at its widest extent.
 */
function dropCovered(clusters) {
    const span = c => c.end.get([...c.end.keys()][0]) - c.start.get([...c.start.keys()][0]) + c.size
    const sorted = [...clusters].sort((a, b) => span(b) - span(a))
    const kept = []
    for (const c of sorted) {
        const covered = kept.some(k => k.kind === c.kind && [...c.start.keys()].every(f => {
            if (!k.start.has(f)) return false
            return c.start.get(f) <= k.end.get(f) + k.size - 1 && k.start.get(f) <= c.end.get(f) + c.size - 1
        }))
        if (!covered) kept.push(c)
    }
    return kept.map(c => ({
        kind: c.kind,
        count: c.count,
        lines: span(c),
        sample: c.sample,
        spans: [...c.start].map(([file, start]) => ({file, start, end: c.end.get(file) + c.size - 1})),
    }))
}

const reports = dropCovered(cluster(matches))
reports.sort((a, b) => b.count - a.count || b.lines - a.lines)

for (const r of reports) {
    const sample = r.sample.find(Boolean)?.slice(0, 80) ?? ''
    const head = `Repeated ${r.kind} block (${r.lines} lines, ${r.count}x across ${r.spans.length} files): ${sample}…`
    const msg = `${head}\n      occurrences:\n        - ${
        r.spans.map(s => `${rel(s.file)}:${s.start}-${s.end}`).join('\n        - ')
    }`
    if (r.count >= ERROR_AT) {
        error(null, 0, msg, CATEGORY)
    } else {
        warn(null, 0, msg, CATEGORY)
    }
}

console.log(
    `\n\x1b[1mDuplication Check\x1b[0m`
    + ` (script window=${SCRIPT_WINDOW}, template window=${TEMPLATE_WINDOW},`
    + ` min-files=${MIN_FILES}, warn @ ${WARN_AT} / error @ ${ERROR_AT})`,
)
reporter.print()
reporter.exit()
