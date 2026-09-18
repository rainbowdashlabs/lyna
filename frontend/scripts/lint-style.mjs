#!/usr/bin/env node
/**
 * Code-style linter for the Ember frontend.
 *
 * A bundle of small regex-based bad-pattern checks. Each is cheap, has
 * obvious meaning, and either errors or warns by itself.
 *
 *   - Empty catch blocks                  (warn, src/)
 *   - Explicit `any` types                (warn, src/, excludes .d.ts)
 *   - `console.log` / `console.debug`     (warn, src/ outside debug helpers)
 *   - `<template>` v-for without :key     (error, src/**.vue)
 *   - TODO / FIXME / XXX / HACK markers   (warn, src/)
 *   - Long functions ( >= --fn-warn lines / >= --fn-error lines )
 */

import {readFileSync} from 'fs'
import {relative, sep} from 'path'
import {SRC, walk, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const {warn, error} = reporter
const CAT_CATCH = 'Empty catch'
const CAT_ANY = 'Explicit any'
const CAT_CONSOLE = 'Leftover console'
const CAT_KEY = 'v-for without :key'
const CAT_TODO = 'TODO marker'
const CAT_FN = 'Long function'

const args = new Map()
for (const arg of process.argv.slice(2)) {
    const m = arg.match(/^--([\w-]+)=(.+)$/)
    if (m) args.set(m[1], m[2])
}

const FN_WARN = Number(args.get('fn-warn') ?? process.env.LINT_FN_WARN ?? 40)
const FN_ERROR = Number(args.get('fn-error') ?? process.env.LINT_FN_ERROR ?? 80)

const tsAndVueFiles = [
    ...walk(SRC, '.vue'),
    ...walk(SRC, '.ts'),
].filter(f => !f.endsWith('.d.ts'))

const EMPTY_CATCH = /catch\s*(\([^)]*\)\s*)?\{\s*\}/g
const ANY_TYPE = /:\s*any\b|\bas\s+any\b/g
const CONSOLE_CALL = /\bconsole\.(log|debug)\s*\(/g
const TODO_MARK = /\b(TODO|FIXME|XXX|HACK)\b/g

for (const file of tsAndVueFiles) {
    const text = readFileSync(file, 'utf-8')
    const lines = text.split('\n')
    const relPath = relative(SRC, file)
    const isDebugHelper = relPath.includes(`debug${sep}`)
        || relPath.endsWith('init.client.ts')
        || relPath.includes(`plugins${sep}`)

    for (const m of text.matchAll(EMPTY_CATCH)) {
        const line = text.slice(0, m.index ?? 0).split('\n').length
        warn(file, line, 'Empty catch block - swallow with a comment or a log instead.', CAT_CATCH)
    }
    for (const m of text.matchAll(ANY_TYPE)) {
        const line = text.slice(0, m.index ?? 0).split('\n').length
        const segment = lines[line - 1] ?? ''
        if (segment.trim().startsWith('//') || segment.trim().startsWith('*')) continue
        warn(file, line, 'Explicit `any` - prefer a precise type.', CAT_ANY)
    }
    if (!isDebugHelper) {
        for (const m of text.matchAll(CONSOLE_CALL)) {
            const line = text.slice(0, m.index ?? 0).split('\n').length
            const segment = lines[line - 1] ?? ''
            if (segment.trim().startsWith('//')) continue
            warn(file, line, `Leftover console.${m[1]} - remove or downgrade to a logger.`, CAT_CONSOLE)
        }
    }
    for (const m of text.matchAll(TODO_MARK)) {
        const line = text.slice(0, m.index ?? 0).split('\n').length
        const segment = lines[line - 1] ?? ''
        if (!/(\/\/|\/\*|\*|<!--)/.test(segment)) continue
        warn(file, line, `${m[1]} marker - track it or remove it.`, CAT_TODO)
    }
}

for (const file of walk(SRC, '.vue')) {
    const text = readFileSync(file, 'utf-8')
    const templateStart = text.indexOf('<template>')
    const templateEnd = text.lastIndexOf('</template>')
    if (templateStart === -1 || templateEnd === -1) continue
    const template = text.slice(templateStart, templateEnd)
    const lineOffset = text.slice(0, templateStart).split('\n').length - 1
    const templateLines = template.split('\n')
    for (let i = 0; i < templateLines.length; i++) {
        const line = templateLines[i]
        if (!/\bv-for=/.test(line)) continue
        const window = templateLines.slice(Math.max(0, i - 2), i + 12).join('\n')
        const elementEnd = findElementEnd(window, window.indexOf('v-for='))
        const elementText = elementEnd === -1 ? window : window.slice(0, elementEnd + 1)
        if (!/(:key|v-bind:key)\s*=/.test(elementText)) {
            error(file, lineOffset + i + 1, 'v-for missing :key - Vue will mis-update siblings on re-render.', CAT_KEY)
        }
    }
}

const FN_OPEN = /(?:^|\n)\s*(?:export\s+)?(?:async\s+)?function\s+([\w$]+)\s*\([^)]*\)\s*(?::[^{]+)?\{/g
const ARROW_FN_OPEN = /(?:^|\n)\s*(?:export\s+)?(?:const|let)\s+([\w$]+)\s*(?::\s*[^=]+)?=\s*(?:async\s+)?\([^)]*\)\s*(?::[^{]+)?=>\s*\{/g

function measureFunction(text, startIdx) {
    let depth = 0
    for (let i = startIdx; i < text.length; i++) {
        const ch = text[i]
        if (ch === '{') depth++
        else if (ch === '}') {
            depth--
            if (depth === 0) return i
        }
    }
    return -1
}

function isComposableWrapper(file, name) {
    if (!name.startsWith('use')) return false
    if (file.includes(`${sep}composables${sep}`)) return true
    return /\buse[A-Z]\w*\.(?:ts|vue)$/.test(file)
}

function scanFunctions(file, text, scanStart, scanEnd) {
    const body = text.slice(scanStart, scanEnd)
    for (const re of [FN_OPEN, ARROW_FN_OPEN]) {
        for (const m of body.matchAll(re)) {
            const openIdx = scanStart + (m.index ?? 0) + m[0].lastIndexOf('{')
            const close = measureFunction(text, openIdx)
            if (close === -1) continue
            const openLine = text.slice(0, openIdx).split('\n').length
            const closeLine = text.slice(0, close).split('\n').length
            const span = closeLine - openLine + 1
            if (isComposableWrapper(file, m[1])) continue
            if (span >= FN_ERROR) {
                error(file, openLine, `function ${m[1]} spans ${span} lines (>= ${FN_ERROR}). Extract helpers.`, CAT_FN)
            } else if (span >= FN_WARN) {
                warn(file, openLine, `function ${m[1]} spans ${span} lines (>= ${FN_WARN}). Consider extracting helpers.`, CAT_FN)
            }
        }
    }
}

for (const file of tsAndVueFiles) {
    const text = readFileSync(file, 'utf-8')
    if (file.endsWith('.ts')) {
        scanFunctions(file, text, 0, text.length)
    } else {
        const scriptStart = text.indexOf('<script')
        const scriptOpen = scriptStart === -1 ? -1 : text.indexOf('>', scriptStart) + 1
        const scriptEnd = text.indexOf('</script>')
        if (scriptOpen !== -1 && scriptEnd !== -1) {
            scanFunctions(file, text, scriptOpen, scriptEnd)
        }
    }
}

function findElementEnd(text, startIdx) {
    if (startIdx < 0) return -1
    let i = startIdx
    let quote = null
    while (i < text.length) {
        const ch = text[i]
        if (quote) {
            if (ch === quote) quote = null
        } else {
            if (ch === '"' || ch === '\'' || ch === '`') quote = ch
            else if (ch === '>') return i
        }
        i++
    }
    return -1
}

console.log(`\n\x1b[1mStyle Check\x1b[0m (fn warn >= ${FN_WARN} / error >= ${FN_ERROR})`)
reporter.print()
reporter.exit()
