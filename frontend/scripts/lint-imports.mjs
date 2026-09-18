#!/usr/bin/env node
/**
 * Import-edge linter for the Ember frontend.
 *
 * The codebase has a rough layering. Layers at the bottom must not pull
 * from layers above them; otherwise the layout devolves into a graph
 * with cycles and pretending to extract a piece means dragging half the
 * app along.
 *
 * Enforced edges:
 *   - src/api/*         → must NOT import from views/, components/,
 *                          composables/, layouts/, pages/
 *   - src/components/*  → must NOT import from views/, layouts/, pages/
 *   - src/composables/* → must NOT import from views/, layouts/, pages/
 *   - src/i18n/*        → must NOT import from views/, components/,
 *                          composables/, layouts/, pages/
 *
 * Imports are detected by static `import` / `export from` statements
 * pointing at `@/<segment>/...` or relative paths that resolve under the
 * forbidden segments. Dynamic `import('@/views/...')` calls are also
 * flagged.
 */

import {readFileSync} from 'fs'
import {dirname, relative, resolve, sep} from 'path'
import {SRC, walk, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const {error} = reporter
const CATEGORY = 'Import direction'

const RULES = [
    {layer: 'api', forbidden: ['views', 'components', 'composables', 'layouts', 'pages']},
    {layer: 'components', forbidden: ['views', 'layouts', 'pages']},
    {layer: 'composables', forbidden: ['views', 'layouts', 'pages']},
    {layer: 'i18n', forbidden: ['views', 'components', 'composables', 'layouts', 'pages']},
]

const IMPORT_RE = /(?:^|\n)\s*(?:import\s+(?:[\w*${}\s,]+\s+from\s+)?|export\s+[*{][\w*${}\s,]*}?\s+from\s+|import\s*\()\s*['"]([^'"]+)['"]\s*\)?/g

function layerOf(relPath) {
    const segments = relPath.split(sep)
    return segments[0] ?? null
}

function resolveImport(file, spec) {
    if (spec.startsWith('@/')) return spec.slice(2)
    if (spec.startsWith('.')) {
        const resolved = resolve(dirname(file), spec)
        return relative(SRC, resolved)
    }
    return null
}

const files = [
    ...walk(SRC, '.ts'),
    ...walk(SRC, '.vue'),
].filter(f => !f.endsWith('.d.ts'))

for (const file of files) {
    const relPath = relative(SRC, file)
    const fileLayer = layerOf(relPath)
    if (!fileLayer) continue
    const rule = RULES.find(r => r.layer === fileLayer)
    if (!rule) continue
    const text = readFileSync(file, 'utf-8')
    for (const m of text.matchAll(IMPORT_RE)) {
        const spec = m[1]
        const resolved = resolveImport(file, spec)
        if (!resolved) continue
        const target = layerOf(resolved)
        if (!target || !rule.forbidden.includes(target)) continue
        const line = text.slice(0, m.index ?? 0).split('\n').length
        error(
            file,
            line,
            `${fileLayer}/ may not import from ${target}/. (imported: ${spec})`,
            CATEGORY,
        )
    }
}

console.log(`\n\x1b[1mImport-Edge Check\x1b[0m`)
reporter.print()
reporter.exit()
