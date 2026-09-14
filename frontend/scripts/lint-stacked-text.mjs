#!/usr/bin/env node
/**
 * Stacked Inline Text Linter
 *
 * Typography building blocks that render a `span` are inline, and Vue drops the whitespace
 * between two elements when it contains a line break. Two of them written one under the other
 * therefore reach the page with nothing at all between their texts, and the `space-y-*` of the
 * surrounding container cannot help: vertical spacing does not apply to inline boxes. What was
 * meant as two lines arrives as one run-on sentence, which is what a reader reports as a
 * missing space.
 *
 * The remedy is to render a block instead, which is what the `tag` property of such a component
 * is for.
 *
 * The check is deliberately narrow, so that what it reports is worth reading:
 *
 * - Only the typography components under `src/components/typography` count, and only those whose
 *   template root is a `span` (directly, or through a `tag` property that defaults to `span`)
 *   and that carry a slot, because a component without text of its own has nothing to glue.
 * - Both elements must be immediate siblings of the same parent with nothing but whitespace
 *   between them, so a component sitting inside a sentence is never reported.
 * - The parent must not lay its children out itself. A flex or grid container turns every child
 *   into a block of its own and separates them with its gap, and a horizontal `space-x-*` row
 *   reaches inline children as well, so neither can produce the fault.
 * - That whitespace must contain a line break, because only then does Vue remove it.
 * - A second element carrying `v-else` or `v-else-if` is skipped: only one of the two is ever
 *   rendered.
 * - A usage that sets `tag` to something other than `span` is not inline, and a usage that binds
 *   `tag` dynamically is left alone rather than guessed at. A usage that carries a display class
 *   of its own already renders as a block and is left alone too.
 * - The second of the two must not bring its own horizontal margin or padding. A component
 *   written with one stands beside what comes before it on purpose, in a running line, and is
 *   already separated from it.
 *
 * It sees nothing about plain `span` elements written out by hand, about components outside the
 * typography folder, about a pair separated by an expression that renders to whitespace, or about
 * a pair that only meets once an element written between them turns out not to render. Those last
 * ones would need the conditions read as well, and a check that guesses at conditions is a check
 * that cries wolf.
 *
 * Exit code 1 if a stacked pair is found.
 */

import {readFileSync} from 'fs'
import {basename} from 'path'
import {SRC, walk, extractTemplate, GREEN, RESET, BOLD, createReporter} from './lint-utils.mjs'

const reporter = createReporter()

const TYPOGRAPHY_DIR = `${SRC}/components/typography`
const VOID_ELEMENTS = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'source', 'track', 'wbr'])
const TAG_PATTERN = /<(\/)?([A-Za-z][\w.-]*)((?:"[^"]*"|'[^']*'|[^>])*?)(\/)?>/g
const DISPLAY_CLASS = /(^|[\s:])(block|flow-root|flex|grid|table|contents)(\s|$)/
const SIDEWAYS_SPACING_CLASS = /(^|[\s:])[mp][lsx]-/

/**
 * Reads the typography folder and returns, per component name, whether it renders an inline
 * `span` that carries slotted text and which property can turn it into something else.
 */
function inlineTypography() {
    const components = new Map()
    for (const file of walk(TYPOGRAPHY_DIR, '.vue')) {
        const content = readFileSync(file, 'utf-8')
        const template = extractTemplate(content)
        if (!template.includes('<slot')) continue
        const root = template.match(/<template>\s*<([A-Za-z][\w.-]*)([^>]*)>/)
        if (!root) continue
        const name = basename(file, '.vue')
        if (root[1] === 'span') {
            components.set(name, {tagProperty: null})
            continue
        }
        const bound = root[2].match(/:is="(\w+)"/)
        if (root[1] !== 'component' || !bound) continue
        if (!new RegExp(`${bound[1]}:\\s*'span'`).test(content)) continue
        components.set(name, {tagProperty: bound[1]})
    }
    return components
}

/**
 * Builds the element tree of a template, so that only true siblings are compared.
 */
function parse(template) {
    const root = {children: [], end: 0}
    const stack = [root]
    for (const match of template.matchAll(TAG_PATTERN)) {
        const [text, closing, name, attributes, selfClosing] = match
        const end = match.index + text.length
        if (closing) {
            if (stack.length > 1) stack.pop().end = end
            continue
        }
        const node = {name, attributes, start: match.index, end, children: []}
        stack[stack.length - 1].children.push(node)
        if (!selfClosing && !VOID_ELEMENTS.has(name)) stack.push(node)
    }
    return root
}

/**
 * The static classes an element carries, as one string.
 */
function classesOf(node) {
    if (node.attributes === undefined) return ''
    return [...node.attributes.matchAll(/class="([^"]*)"/g)].map(match => match[1]).join(' ')
}

/**
 * Decides whether a usage of a typography component reaches the page as an inline box.
 */
function rendersInline(node, components) {
    const component = components.get(node.name)
    if (!component) return false
    if (DISPLAY_CLASS.test(classesOf(node))) return false
    if (!component.tagProperty) return true
    if (new RegExp(`[:@]${component.tagProperty}=`).test(node.attributes)) return false
    const literal = node.attributes.match(new RegExp(`\\s${component.tagProperty}="([^"]*)"`))
    return !literal || literal[1] === 'span'
}

/**
 * Decides whether an element is the other branch of the one before it, in which case the two
 * never stand on the page together.
 */
function isAlternative(node) {
    return /\sv-else\b/.test(node.attributes) || /\sv-else-if=/.test(node.attributes)
}

/**
 * Decides whether a parent places its children itself, in which case an inline child is
 * blockified or separated and the fault cannot arise.
 */
function laysOutChildren(node) {
    const classes = classesOf(node)
    return /(^|[\s:])(inline-)?(flex|grid)(\s|$)/.test(classes) || /(^|[\s:])space-x-/.test(classes)
}

/**
 * Decides whether an element brings its own separation from what stands before it.
 */
function standsBeside(node) {
    return SIDEWAYS_SPACING_CLASS.test(classesOf(node))
}

/**
 * The one-based line an offset falls on.
 */
function lineOf(text, index) {
    return text.slice(0, index).split('\n').length
}

const components = inlineTypography()
const files = walk(SRC, '.vue')

for (const file of files) {
    const content = readFileSync(file, 'utf-8')
    const template = extractTemplate(content)
    if (!template) continue
    const offset = lineOf(content, content.indexOf(template)) - 1
    const queue = [parse(template)]
    while (queue.length > 0) {
        const parent = queue.pop()
        queue.push(...parent.children)
        if (laysOutChildren(parent)) continue
        for (let i = 1; i < parent.children.length; i++) {
            const previous = parent.children[i - 1]
            const current = parent.children[i]
            const gap = template.slice(previous.end, current.start)
            if (gap.trim() !== '' || !gap.includes('\n')) continue
            if (isAlternative(current) || standsBeside(current)) continue
            if (!rendersInline(previous, components) || !rendersInline(current, components)) continue
            reporter.error(
                file,
                offset + lineOf(template, current.start),
                `<${current.name}> follows <${previous.name}> as an inline sibling, so their texts run together. `
                + 'Give both of them tag="p".',
            )
        }
    }
}

if (reporter.errors.length === 0 && reporter.warnings.length === 0) {
    console.log(`\n${GREEN}${BOLD}Stacked inline text lint passed.${RESET} `
        + `${files.length} templates, ${components.size} inline typography components.\n`)
} else {
    reporter.print()
    reporter.exit()
}
