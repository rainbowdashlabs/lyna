#!/usr/bin/env node
/**
 * Locale String Linter
 *
 * Checks i18n locale files for common issues:
 * - Unescaped '@' characters (vue-i18n interprets @ as linked message syntax)
 * - Unescaped '|' characters (vue-i18n interprets | as plural separator)
 * - Unbalanced curly braces in interpolation
 *
 * Exit code 1 if any errors are found.
 */

import {readFileSync} from 'fs'
import {join} from 'path'
import {SRC, RED, GREEN, YELLOW, RESET, BOLD, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const LOCALE_DIR = join(SRC, 'i18n', 'locales')

// ── Load locale files ────────────────────────────────────────────────

import {readdirSync} from 'fs'

const localeFiles = readdirSync(LOCALE_DIR)
    .filter(f => f.endsWith('.json'))

for (const file of localeFiles) {
    const filePath = join(LOCALE_DIR, file)
    const content = readFileSync(filePath, 'utf-8')
    const lines = content.split('\n')

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i]
        const lineNum = i + 1

        // Only check lines that contain string values (inside quotes)
        const stringMatch = line.match(/:\s*(['"`])(.+)\1/)
        if (!stringMatch) continue

        const quote = stringMatch[1]
        const value = stringMatch[2]

        // Skip template literals that use {'@'} escaping - those are fine
        // Check for bare @ not inside {'...'} escape blocks
        checkUnescapedChar(value, '@', filePath, lineNum, quote)
        checkUnescapedChar(value, '|', filePath, lineNum, quote)

        // Check for unbalanced { } outside of {'...'} escapes
        checkBraces(value, filePath, lineNum)
    }
}

/**
 * Check for unescaped special characters that are not inside {'...'} blocks.
 */
function checkUnescapedChar(value, char, file, line, quote) {
    // Remove all {'...'} escape blocks first
    const stripped = value.replace(/\{'\\.?'}/g, '').replace(/\{'[^']*'}/g, '')

    // Also remove interpolation blocks like {name} - those are valid
    const withoutInterpolation = stripped.replace(/\{[a-zA-Z_][a-zA-Z0-9_]*}/g, '')

    if (withoutInterpolation.includes(char)) {
        const col = value.indexOf(char) + 1
        reporter.error(file, line,
            `Unescaped '${char}' in locale string - use {'${char}'} to escape it (column ${col})`,
            'Locale syntax')
    }
}

/**
 * Check for unbalanced braces outside of escape blocks.
 */
function checkBraces(value, file, line) {
    // Remove escape blocks {'...'} and interpolation {name}
    const stripped = value
        .replace(/\{'[^']*'}/g, '')
        .replace(/\{[a-zA-Z_][a-zA-Z0-9_]*}/g, '')
        .replace(/\{[0-9]+}/g, '')

    const opens = (stripped.match(/\{/g) || []).length
    const closes = (stripped.match(/}/g) || []).length

    if (opens !== closes) {
        reporter.error(file, line,
            `Unbalanced curly braces: ${opens} opening, ${closes} closing`,
            'Locale syntax')
    }
}

// ── Results ──────────────────────────────────────────────────────────

reporter.print()
reporter.exit()
