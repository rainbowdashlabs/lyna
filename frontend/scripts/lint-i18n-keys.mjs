#!/usr/bin/env node
/**
 * i18n key linter for the Ember frontend.
 *
 * Two checks:
 *   1. Missing keys: every t('foo.bar') / $t('foo.bar') / te('foo.bar') call
 *      under src/ must resolve to a key defined in src/i18n/locales/en-US.json (merged into the
 *      locale at runtime under that prefix).
 *   2. Unused keys: every leaf key defined in src/i18n/locales/en-US.json must be
 *      referenced at least once.
 *
 * Tunables:
 *   --check-unused=false   suppress the unused-key warnings
 *   --warn-unused          report unused keys at warn level (default)
 *   --error-unused         report unused keys at error level
 *
 * Heuristics:
 *   - Dynamic keys (template literals, string concatenation, computed keys)
 *     are skipped - there's no static analysis that resolves them
 *     reliably without an AST. Both ends of the check therefore err on
 *     the side of false negatives over false positives.
 *   - Keys that are themselves dynamic prefixes (e.g. `pages.${name}.title`)
 *     short-circuit to a "any leaf under this prefix counts" allow-list,
 *     so static usage detection doesn't flag every page title as unused.
 *   - Template literals with an interpolation are treated as dynamic prefixes
 *     wherever they appear, not only inside a t() call: help pages and error
 *     mappers build the key into a local first. The static part only counts
 *     when it actually prefixes a defined key, so unrelated literals (URLs,
 *     file names) never register.
 *   - The mirror image of that: a template literal whose *head* is the
 *     interpolation and whose tail is static (`${props.i18nPrefix}.form.title`)
 *     is a component parameterised by prefix. The static tails are paired with
 *     the prefix values callers pass through an `i18n-prefix` prop, and every
 *     combination that spells a defined key counts as a reference. Only
 *     combinations that are actually defined register, so a prefix and a tail
 *     that never meet in the locale rescue nothing.
 *   - tm() resolves a whole message subtree - every leaf below the key it is
 *     given counts as referenced.
 *   - A plain string literal that is character-for-character a defined key
 *     counts as a reference. Route tables, error-code maps and *Key props pass
 *     keys around as data and hand them to t() somewhere else entirely.
 *   - <i18n-t keypath="..."> is a reference like t() is.
 *   - The backend cross-checks (enum-backed sections, keys the server sends as
 *     data) need the Java sources beside this frontend. The frontend Docker
 *     image copies only `frontend/`, so there they cannot run at all; they and
 *     the unused-key scan that depends on them stand down with one warning
 *     rather than reporting every file as missing.
 *   - Enum-backed sections: some i18n sections mirror a backend Java enum and
 *     are only ever referenced through dynamic keys, which the usage scan
 *     cannot resolve. Those sections are declared in ENUM_BACKED_SECTIONS and
 *     checked directly against the enum's constants: every constant must have
 *     all its leaf keys, and ALL_CAPS entries without a matching constant are
 *     reported as stale.
 */

import {existsSync, readFileSync} from 'fs'
import {join} from 'path'
import {SRC, walk, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const {warn, error} = reporter
const CAT_MISSING = 'Missing i18n key'
const CAT_UNUSED = 'Unused i18n key'
const CAT_ENUM = 'Enum-backed i18n section'

const args = new Map()
for (const arg of process.argv.slice(2)) {
    if (arg === '--check-unused=false') args.set('check-unused', 'false')
    else if (arg === '--error-unused') args.set('unused-severity', 'error')
    else {
        const m = arg.match(/^--([\w-]+)=(.+)$/)
        if (m) args.set(m[1], m[2])
    }
}

const CHECK_UNUSED = (args.get('check-unused') ?? 'true') !== 'false'
const UNUSED_SEVERITY = args.get('unused-severity') ?? 'warn'

const I18N_FILE = `${SRC}/i18n/locales/en-US.json`

/**
 * Flattens a JSON locale document into its dotted leaf keys. A leaf is any value that is not an
 * object, so an array of plural forms counts as one key rather than one key per form.
 */
function collectKeys(text) {
    const keys = new Set()
    const visit = (node, prefix) => {
        for (const [name, value] of Object.entries(node)) {
            const key = prefix ? `${prefix}.${name}` : name
            if (value && typeof value === 'object' && !Array.isArray(value)) visit(value, key)
            else keys.add(key)
        }
    }
    visit(JSON.parse(text), '')
    return keys
}

const definedKeys = collectKeys(readFileSync(I18N_FILE, 'utf-8'))

const usedExact = new Set()
const usedPrefixes = new Set()

const T_CALL = /\b(?:\$)?(?:t|te|tc|tm)\s*\(\s*(['"`])([^'"`]+)\1\s*[,)]/g
const TM_CALL = /\b(?:\$)?tm\s*\(\s*(['"`])([^'"`]+)\1\s*[,)]/g
const T_CALL_CONCAT_PREFIX = /\b(?:\$)?(?:t|te|tc|tm)\s*\(\s*(['"`])([^'"`]+)\1\s*\+/g
const TEMPLATE_PREFIX = /`([A-Za-z][\w-]*(?:\.[\w-]*)+)\$\{/g
const TEMPLATE_SUFFIX = /`\$\{[^`{}]*\}((?:\.[\w-]+)+)`/g
const PREFIX_PROP = /\bi18n[-_]?prefix\s*=\s*"([A-Za-z][\w-]*(?:\.[\w-]+)*)"/gi
const KEYPATH_ATTR = /\bkeypath\s*=\s*(['"])([^'"]+)\1/g
const KEY_LITERAL = /(['"`])([A-Za-z][\w-]*(?:\.[\w-]+)+)\1/g

const sortedKeys = [...definedKeys].sort()

/**
 * True when at least one defined key starts with the given text. The static
 * head of a template literal is only accepted as a dynamic prefix when it
 * actually leads into the key space, which keeps unrelated literals such as
 * URLs and class names out of the allow-list. Partial trailing segments
 * (`quiz.batch.action_`) count, so a plain segment lookup is not enough.
 */
function prefixesDefinedKey(text) {
    let lo = 0
    let hi = sortedKeys.length
    while (lo < hi) {
        const mid = (lo + hi) >> 1
        if (sortedKeys[mid] < text) lo = mid + 1
        else hi = mid
    }
    return lo < sortedKeys.length && sortedKeys[lo].startsWith(text)
}

const files = [...walk(SRC, '.vue'), ...walk(SRC, '.ts')]
    .filter(f => f !== I18N_FILE)

const prefixPropValues = new Set()
const dynamicHeadSuffixes = new Set()

for (const file of files) {
    const text = readFileSync(file, 'utf-8')
    for (const m of text.matchAll(T_CALL)) {
        const key = m[2]
        if (!key.includes('.')) continue
        if (key.includes('${')) {
            const prefix = key.split('${')[0].replace(/\.+$/, '')
            if (prefix) usedPrefixes.add(prefix)
            continue
        }
        usedExact.add(key)
        if (!definedKeys.has(key)) {
            const offset = m.index ?? 0
            const line = text.slice(0, offset).split('\n').length
            error(file, line, `i18n key not defined: t('${key}')`, CAT_MISSING)
        }
    }
    for (const m of text.matchAll(TM_CALL)) {
        const key = m[2]
        if (key.includes('.') && !key.includes('${')) usedPrefixes.add(key)
    }
    for (const m of text.matchAll(T_CALL_CONCAT_PREFIX)) {
        const prefix = m[2].replace(/\.+$/, '')
        if (prefix.length > 0) usedPrefixes.add(prefix)
    }
    for (const m of text.matchAll(TEMPLATE_PREFIX)) {
        if (!prefixesDefinedKey(m[1])) continue
        const prefix = m[1].replace(/\.+$/, '')
        if (prefix.length > 0) usedPrefixes.add(prefix)
    }
    for (const m of text.matchAll(TEMPLATE_SUFFIX)) {
        dynamicHeadSuffixes.add(m[1])
    }
    for (const m of text.matchAll(PREFIX_PROP)) {
        prefixPropValues.add(m[1])
    }
    for (const m of text.matchAll(KEYPATH_ATTR)) {
        const key = m[2]
        if (definedKeys.has(key)) usedExact.add(key)
    }
    for (const m of text.matchAll(KEY_LITERAL)) {
        if (definedKeys.has(m[2])) usedExact.add(m[2])
    }
}

for (const prefix of prefixPropValues) {
    for (const suffix of dynamicHeadSuffixes) {
        const key = prefix + suffix
        if (definedKeys.has(key)) usedExact.add(key)
    }
}

const REPO_ROOT = new URL('../..', import.meta.url).pathname

/**
 * Locale sections whose keys are one per constant of a backend enum. `leaves` names the keys
 * below each constant; a section without it carries the translation on the constant itself,
 * which is the shape of a plain label map.
 *
 * Empty while no locale section mirrors a backend enum. Adding one here is what keeps a new
 * enum constant from reaching the page as its raw name.
 */
const ENUM_BACKED_SECTIONS = []

/**
 * Backend sources that hand i18n keys to the frontend as data. Keys named here count as
 * referenced, and a key the backend sends but the locale does not define is a missing
 * translation.
 *
 * Empty while the backend sends no locale keys of its own; its payloads carry values the
 * frontend labels itself.
 */
const BACKEND_KEY_FILES = []

const TOP_LEVEL_NAMESPACES = new Set([...definedKeys].map(key => key.split('.')[0]))

/**
 * Whether the backend sources sit next to this frontend at all. The Docker image for the
 * frontend copies only `frontend/`, so there the answer is no and every cross-check below has
 * nothing to read. That is a property of the checkout, not a defect in the locale, so those
 * checks stand down instead of failing. A *missing individual file* inside a full checkout
 * still errors - that one means a rename nobody followed here.
 *
 * The unused-key scan stands down with them: the backend files are one of the two places a key
 * can be referenced from, so without them every key only the backend sends reads as unused.
 */
const backendSourcesPresent = existsSync(join(REPO_ROOT, 'src/main/java'))

if (backendSourcesPresent) {
    for (const file of BACKEND_KEY_FILES) {
        let text
        try {
            text = readFileSync(join(REPO_ROOT, file), 'utf-8')
        } catch {
            error(I18N_FILE, 0, `backend key source not found: ${file}`, CAT_MISSING)
            continue
        }
        for (const m of text.matchAll(/"([a-zA-Z][\w-]*(?:\.[\w-]+)+)"/g)) {
            const key = m[1]
            if (!TOP_LEVEL_NAMESPACES.has(key.split('.')[0])) continue
            usedExact.add(key)
            if (!definedKeys.has(key)) {
                error(I18N_FILE, 0, `i18n key sent by ${file} is not defined: ${key}`, CAT_MISSING)
            }
        }
    }
}

/**
 * Extracts the constant names of a Java enum: comments are stripped, the
 * constant list ends at the first semicolon, and names inside parentheses or
 * braces (constructor arguments, constant bodies) are ignored so that only
 * top-level declarations count.
 */
function parseJavaEnumConstants(text) {
    const stripped = text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*/g, '')
    const body = stripped.slice(stripped.indexOf('{') + 1).split(';')[0]
    const constants = new Set()
    let depth = 0
    for (const token of body.matchAll(/([A-Z][A-Z0-9_]*)|([({])|([)}])/g)) {
        if (token[2]) depth++
        else if (token[3]) depth--
        else if (depth === 0) constants.add(token[1])
    }
    return constants
}

if (backendSourcesPresent) {
    for (const section of ENUM_BACKED_SECTIONS) {
        let text
        try {
            text = readFileSync(join(REPO_ROOT, section.enumFile), 'utf-8')
        } catch {
            error(I18N_FILE, 0, `enum file not found: ${section.enumFile}`, CAT_ENUM)
            continue
        }
        const constants = parseJavaEnumConstants(text)
        for (const constant of constants) {
            const keys = section.leaves
                ? section.leaves.map(leaf => `${section.prefix}.${constant}.${leaf}`)
                : [`${section.prefix}.${constant}`]
            for (const key of keys) {
                if (!definedKeys.has(key)) {
                    error(I18N_FILE, 0, `missing translation for enum value ${constant}: ${key}`, CAT_ENUM)
                }
            }
        }
        const stalePattern = new RegExp(`^${section.prefix.replace(/\./g, '\\.')}\\.([A-Z][A-Z0-9_]*)(?:\\.|$)`)
        const reported = new Set()
        for (const key of definedKeys) {
            const m = key.match(stalePattern)
            if (m && !constants.has(m[1]) && !reported.has(m[1])) {
                reported.add(m[1])
                warn(I18N_FILE, 0, `stale ${section.prefix} entry without matching enum value: ${m[1]}`, CAT_ENUM)
            }
        }
    }
}

if (!backendSourcesPresent) {
    warn(
        I18N_FILE,
        0,
        'backend sources are not in this checkout - the cross-checks against the Java enums and payloads, '
        + 'and the unused-key scan that depends on them, are skipped',
        CAT_MISSING,
    )
}

if (CHECK_UNUSED && backendSourcesPresent) {
    for (const key of definedKeys) {
        if (usedExact.has(key)) continue
        let matched = false
        for (const pfx of usedPrefixes) {
            if (key.startsWith(pfx + '.') || key.startsWith(pfx)) { matched = true; break }
        }
        if (matched) continue
        const msg = `i18n key defined but never referenced: ${key}`
        if (UNUSED_SEVERITY === 'error') error(I18N_FILE, 0, msg, CAT_UNUSED)
        else warn(I18N_FILE, 0, msg, CAT_UNUSED)
    }
}

console.log(`\n\x1b[1mi18n Key Check\x1b[0m (defined: ${definedKeys.size}, referenced: ${usedExact.size})`)
reporter.print()
reporter.exit()
