#!/usr/bin/env node
/**
 * Block-size and div-density linter for the Ember frontend.
 *
 * Two checks live here. Both target pages (and exempt src/components/) because
 * the components dir is where the extracted pieces are supposed to land.
 *
 * Both checks are single-tier: they report an error or they report nothing.
 * The advisory warning tiers were removed once their distributions were
 * measured - the 30-line block warning and the 4-child density warning had no
 * outlier tail, so they flagged ordinary Tailwind markup rather than a smell,
 * and several hundred findings nobody acted on hid the checks that do bite.
 *
 * 1. Block size: any single template block (element + its children) that
 *    spans --error=N lines or more (default 50) is a block whose inner content
 *    wants its own component.
 *
 * 2. Section density: any element on the 2nd or 3rd template level with
 *    --div-error=N (default 6) or more direct "section" children - plain
 *    <div>, semantic HTML containers (<section>, <article>, <header>,
 *    <footer>, <main>, <nav>, <aside>), or Ember container components
 *    (NeutralContainer, PrimaryContainer, …) - is a stack of distinct
 *    sections that each want their own component.
 *
 * What is exempt from the block-size check:
 *   - files inside src/components/ (those are the targets for extraction)
 *   - the root <template> element
 *   - common outer wrappers: ViewContent, ViewLayout, SidebarLayout, NuxtPage,
 *     NuxtLayout, HelpArticle, Modal - these define the page shell and the
 *     entire body lives inside them
 *   - void HTML elements (br, hr, img, input, …)
 *   - self-closing tags
 */

import {readFileSync} from 'fs'
import {SRC, walk, extractTemplate, isInsideComponents, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const {error} = reporter
const CAT_BLOCK = 'Block size'
const CAT_DIV = 'Section density'

const SECTION_TAGS = new Set([
    'div', 'section', 'article', 'header', 'footer', 'main', 'nav', 'aside',
    'NeutralContainer', 'PrimaryContainer', 'SecondaryContainer',
    'SuccessContainer', 'ErrorContainer', 'InfoContainer', 'BaseContainer',
])

function isSectionTag(tag) {
    return SECTION_TAGS.has(tag) || SECTION_TAGS.has(tag.toLowerCase())
}

const args = new Map()
for (const arg of process.argv.slice(2)) {
    const m = arg.match(/^--([\w-]+)=(.+)$/)
    if (m) args.set(m[1], m[2])
}

const BLOCK_ERROR = Number(args.get('error') ?? process.env.LINT_BLOCK_ERROR ?? 50)
const DIV_ERROR = Number(args.get('div-error') ?? process.env.LINT_DIV_ERROR ?? 6)

const ROOT_WRAPPER_TAGS = new Set([
    'template',
    'ViewContent',
    'ViewLayout',
    'SidebarLayout',
    'NuxtPage',
    'NuxtLayout',
    'NuxtRoot',
    'HelpArticle',
    'Modal',
])

const VOID_TAGS = new Set([
    'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
    'link', 'meta', 'param', 'source', 'track', 'wbr',
])

const TAG_REGEX = /<(\/?)([A-Za-z][\w-]*)([^>]*?)(\/?)>/g

function lineOfIndex(template, idx) {
    let line = 1
    for (let i = 0; i < idx; i++) {
        if (template.charCodeAt(i) === 10) line++
    }
    return line
}

function reportBlock(file, tag, openLine, span) {
    if (span < BLOCK_ERROR) return
    error(file, openLine, `<${tag}> block spans ${span} lines (>= ${BLOCK_ERROR}). Extract to a component.`, CAT_BLOCK)
}

function reportSectionDensity(file, parentTag, openLine, depth, count) {
    error(file, openLine, `<${parentTag}> at level ${depth} has ${count} direct section children (div / NeutralContainer / section / …) - each section likely wants its own component.`, CAT_DIV)
}

const vueFiles = walk(SRC, '.vue').filter(f => !isInsideComponents(f))

for (const file of vueFiles) {
    const content = readFileSync(file, 'utf-8')
    const template = extractTemplate(content)
    if (!template) continue
    const templateStartLine = content.substring(0, content.indexOf('<template>')).split('\n').length

    const stack = []

    for (const m of template.matchAll(TAG_REGEX)) {
        const [, slash, tag, attrs, selfSlash] = m
        if (VOID_TAGS.has(tag.toLowerCase())) continue
        const closeMatch = slash === '/'
        const selfClosing = selfSlash === '/' || /\/$/.test(attrs.trim())
        if (selfClosing && !closeMatch) continue

        const idxInTemplate = m.index ?? 0
        const tagLine = templateStartLine + lineOfIndex(template, idxInTemplate) - 1

        if (!closeMatch) {
            const depth = stack.length + 1
            if (stack.length > 0 && isSectionTag(tag)) {
                stack[stack.length - 1].sectionChildren++
            }
            stack.push({tag, line: tagLine, depth, sectionChildren: 0})
            continue
        }

        for (let i = stack.length - 1; i >= 0; i--) {
            if (stack[i].tag === tag) {
                const opened = stack[i]
                stack.length = i
                const span = tagLine - opened.line + 1
                const skipBlock = ROOT_WRAPPER_TAGS.has(opened.tag) || opened.depth <= 2
                if (!skipBlock) reportBlock(file, opened.tag, opened.line, span)
                if ((opened.depth === 2 || opened.depth === 3) && opened.sectionChildren >= DIV_ERROR) {
                    reportSectionDensity(file, opened.tag, opened.line, opened.depth, opened.sectionChildren)
                }
                break
            }
        }
    }
}

console.log(
    `\n\x1b[1mTemplate Structure Check\x1b[0m`
    + ` (block error >= ${BLOCK_ERROR} lines,`
    + ` section-density error >= ${DIV_ERROR} children)`,
)
reporter.print()
reporter.exit()
