#!/usr/bin/env node
/**
 * FontAwesome Icon Linter
 *
 * Checks that all icons used in templates are properly imported and
 * registered in library.add() in the FontAwesome Nuxt plugin.
 *
 * Exit code 1 if any unregistered icons are found.
 */

import {readFileSync} from 'fs'
import {SRC, walk, rel, extractTemplate, RED, GREEN, YELLOW, RESET, BOLD, createReporter} from './lint-utils.mjs'
import {join} from 'path'

const reporter = createReporter()
const PLUGIN_TS = join(SRC, 'plugins', 'fontawesome.ts')

// ── Parse registered icons from the FontAwesome Nuxt plugin ─────────

const pluginContent = readFileSync(PLUGIN_TS, 'utf-8')

// Extract every library.add(...) call so the registration list may be
// split across one-per-line statements (better for bundler tree-shaking).
const libraryAddMatches = [...pluginContent.matchAll(/library\.add\(([^)]+)\)/g)]
if (libraryAddMatches.length === 0) {
    console.error(`Could not find library.add() in ${rel(PLUGIN_TS)}`)
    process.exit(1)
}

const registeredVarNames = new Set(
    libraryAddMatches.flatMap(m => m[1].split(',').map(s => s.trim()).filter(Boolean))
)

// Convert FA variable names to icon names: faChevronDown → chevron-down
function faVarToIconName(varName) {
    // Remove 'fa' prefix, then convert camelCase to kebab-case
    const withoutPrefix = varName.replace(/^fa/, '')
    return withoutPrefix
        .replace(/([A-Z])/g, '-$1')
        .toLowerCase()
        .replace(/^-/, '')
}

// Build set of registered icon names (kebab-case)
const registeredIcons = new Map() // iconName → {prefix: 'fas'|'fab'}

// Determine prefix from import source
const solidImportMatch = pluginContent.match(/import\s*\{([^}]+)\}\s*from\s*'@fortawesome\/free-solid-svg-icons'/)
const brandsImportMatch = pluginContent.match(/import\s*\{([^}]+)\}\s*from\s*'@fortawesome\/free-brands-svg-icons'/)

const solidVars = solidImportMatch
    ? new Set(solidImportMatch[1].split(',').map(s => s.trim()).filter(Boolean))
    : new Set()
const brandsVars = brandsImportMatch
    ? new Set(brandsImportMatch[1].split(',').map(s => s.trim()).filter(Boolean))
    : new Set()

for (const varName of registeredVarNames) {
    const iconName = faVarToIconName(varName)
    const prefix = brandsVars.has(varName) ? 'fab' : 'fas'
    registeredIcons.set(`${prefix}:${iconName}`, varName)
}

// ── Scan templates for icon usage ───────────────────────────────────

const vueFiles = walk(SRC, '.vue')
// Icon usage patterns: ['fas', 'icon-name'] or ['fab', 'icon-name']
const iconRegex = /\['(fas|fab)',\s*'([a-z0-9-]+)'\]/g

for (const file of vueFiles) {
    const content = readFileSync(file, 'utf-8')
    const template = extractTemplate(content)
    if (!template) continue

    const templateLines = template.split('\n')
    const templateStartLine = content.substring(0, content.indexOf('<template>')).split('\n').length

    for (let i = 0; i < templateLines.length; i++) {
        const line = templateLines[i]
        let match
        iconRegex.lastIndex = 0
        while ((match = iconRegex.exec(line)) !== null) {
            const [, prefix, iconName] = match
            const key = `${prefix}:${iconName}`
            if (!registeredIcons.has(key)) {
                reporter.error(file, templateStartLine + i,
                    `Icon '${iconName}' (${prefix}) is not registered in library.add(). Add fa${iconName.split('-').map(s => s[0].toUpperCase() + s.slice(1)).join('')} to src/plugins/fontawesome.ts.`)
            }
        }
    }
}

// ── Also check script sections for programmatic icon usage ──────────

for (const file of vueFiles) {
    const content = readFileSync(file, 'utf-8')
    // Check script section too (icons can be referenced in computed/methods)
    const scriptMatch = content.match(/<script[^>]*>([\s\S]*?)<\/script>/)
    if (!scriptMatch) continue

    const script = scriptMatch[1]
    const scriptStartLine = content.substring(0, content.indexOf(scriptMatch[0])).split('\n').length

    const scriptLines = script.split('\n')
    for (let i = 0; i < scriptLines.length; i++) {
        const line = scriptLines[i]
        let match
        iconRegex.lastIndex = 0
        while ((match = iconRegex.exec(line)) !== null) {
            const [, prefix, iconName] = match
            const key = `${prefix}:${iconName}`
            if (!registeredIcons.has(key)) {
                reporter.error(file, scriptStartLine + i,
                    `Icon '${iconName}' (${prefix}) is not registered in library.add(). Add fa${iconName.split('-').map(s => s[0].toUpperCase() + s.slice(1)).join('')} to src/plugins/fontawesome.ts.`)
            }
        }
    }
}

// ── Output ──────────────────────────────────────────────────────────

if (reporter.errors.length === 0) {
    console.log(`\n${GREEN}${BOLD}Icon lint passed.${RESET} All ${registeredIcons.size} registered icons are valid.\n`)
} else {
    reporter.print()
    reporter.exit()
}
