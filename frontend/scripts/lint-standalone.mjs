#!/usr/bin/env node
/**
 * Standalone Checkout Linter
 *
 * Every frontend linter has to pass with nothing but `frontend/` on disk. The frontend Docker
 * image is built from that one directory, and the image build runs the same lint chain this
 * verification does, so a linter that reads something above `frontend/` - a Java source it
 * cross-checks keys against, a config at the repository root - passes every local check and
 * fails only in the image, where the file it wants was never copied.
 *
 * Each linter therefore runs a second time inside a sandbox shaped like the image: a temporary
 * directory whose only entry is a `frontend`, with `scripts/` copied into it so a linter
 * resolves its own location inside the sandbox, and everything else linked so nothing is
 * duplicated. A linter that fails there but passes in the real checkout is reading outside its
 * directory; a linter that fails in both is simply reporting its own findings, which the
 * ordinary lint stage already surfaces.
 *
 * Exit code 1 if any linter depends on files outside frontend/.
 */

import {cpSync, mkdtempSync, readdirSync, rmSync, symlinkSync} from 'fs'
import {spawnSync} from 'child_process'
import {tmpdir} from 'os'
import {basename, join} from 'path'
import {BOLD, RESET, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const SCRIPTS = new URL('.', import.meta.url).pathname
const FRONTEND = join(SCRIPTS, '..')
const SELF = basename(new URL(import.meta.url).pathname)
const CAT_ESCAPES = 'Reads outside frontend/'

const linters = readdirSync(SCRIPTS)
    .filter(f => f.startsWith('lint-') && f.endsWith('.mjs') && f !== 'lint-utils.mjs' && f !== SELF)
    .sort()

/**
 * Builds the frontend-only checkout and returns its root and its `frontend` directory.
 */
function createSandbox() {
    const root = mkdtempSync(join(tmpdir(), 'ember-frontend-only-'))
    const frontend = join(root, 'frontend')
    cpSync(SCRIPTS, join(frontend, 'scripts'), {recursive: true})
    for (const entry of readdirSync(FRONTEND)) {
        if (entry !== 'scripts') symlinkSync(join(FRONTEND, entry), join(frontend, entry))
    }
    return {root, frontend}
}

function runLinter(script, frontend) {
    const result = spawnSync(process.execPath, [join(frontend, 'scripts', script)], {
        cwd: frontend,
        encoding: 'utf-8',
    })
    return {passed: result.status === 0, output: `${result.stdout ?? ''}${result.stderr ?? ''}`.trim()}
}

const sandbox = createSandbox()
const escapes = []

try {
    for (const script of linters) {
        const sandboxed = runLinter(script, sandbox.frontend)
        if (sandboxed.passed) continue
        if (!runLinter(script, FRONTEND).passed) continue
        reporter.error(`scripts/${script}`, 0,
            'fails when only frontend/ is present, so the frontend Docker image cannot build',
            CAT_ESCAPES)
        escapes.push({script, output: sandboxed.output})
    }
} finally {
    rmSync(sandbox.root, {recursive: true, force: true})
}

console.log(`\n${BOLD}Standalone Checkout Check${RESET} (linters: ${linters.length})`)

for (const {script, output} of escapes) {
    console.log(`\n${BOLD}${script} in a frontend-only checkout:${RESET}\n${output}`)
}

reporter.print()
reporter.exit()