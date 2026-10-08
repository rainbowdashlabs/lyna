#!/usr/bin/env node
/**
 * Page Title Linter
 *
 * Every page names itself, so a browser tab, a bookmark and a shared link say what they are. A file
 * under src/pages/ passes when it calls usePageTitle(...), or a use...Preview(...) composable, which
 * sets the title along with the rest of a public page's link preview.
 *
 * Exit code 1 if any page sets no title.
 */

import {readFileSync} from 'fs'
import {PAGES_DIR, walk, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const TITLED = /\b(usePageTitle|use\w*Preview)\s*\(/

for (const file of walk(PAGES_DIR, '.vue')) {
    if (!TITLED.test(readFileSync(file, 'utf-8'))) {
        reporter.error(file, 0, 'Sets no page title. Call usePageTitle(...) in its script.', 'Page titles')
    }
}

reporter.print()
reporter.exit()
