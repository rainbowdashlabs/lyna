/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

export type LogLevel = 'error' | 'warn' | 'info' | 'debug'

export interface LogLine {
    /** One-based, as the gutter shows it and a permalink names it. */
    number: number
    text: string
    level: LogLevel | null
    /**
     * A line that belongs to the one above it: anything not opening with a log prefix, which is how
     * an exception's message, its frames and its `Caused by` follow the line that logged it. Runs of
     * them fold under that line.
     */
    continuation: boolean
}

/** A line and the continuation lines under it, which the viewer folds as one. */
export interface LogBlock {
    head: LogLine
    tail: LogLine[]
    /** The most severe level anywhere in the block, so a folded stack trace still shows as an error. */
    level: LogLevel | null
}

const LEVEL_WORD = /^(?:\[[^\]]*\]\s*)?\[[^\]]*?\b(FATAL|SEVERE|ERROR|WARN(?:ING)?|INFO|DEBUG|TRACE)\]/i
const CONTINUATION = /^(?:\s+at\s|\s*\.\.\.\s\d+\s(?:more|common frames omitted)|Caused by:|\s+Suppressed:|\t)/

const SEVERITY: Record<LogLevel, number> = {debug: 0, info: 1, warn: 2, error: 3}

/**
 * Reads the level a server log line was written at.
 *
 * <p>Vanilla and Paper write `[12:00:00] [Server thread/WARN]: …` and older Spigot `[12:00:00 WARN]: …`;
 * both carry the level as the last word of a bracket at the start, which is all this looks for.
 */
export function levelOf(text: string): LogLevel | null {
    const match = LEVEL_WORD.exec(text)
    if (!match) return null
    const word = match[1]!.toUpperCase()
    if (word === 'FATAL' || word === 'SEVERE' || word === 'ERROR') return 'error'
    if (word.startsWith('WARN')) return 'warn'
    if (word === 'INFO') return 'info'
    return 'debug'
}

export function splitLines(content: string): LogLine[] {
    return content.split(/\r?\n/).map((text, index) => ({
        number: index + 1,
        text,
        level: levelOf(text),
        continuation: CONTINUATION.test(text) || (text.trim().length > 0 && !text.startsWith('[')),
    }))
}

function severer(a: LogLevel | null, b: LogLevel | null): LogLevel | null {
    if (a === null) return b
    if (b === null) return a
    return SEVERITY[a] >= SEVERITY[b] ? a : b
}

/**
 * Folds every run of continuation lines under the line before it. A line with no level of its own
 * inside a block takes the block's, which is how the frames of a stack trace read as part of an error.
 */
export function blocksOf(lines: LogLine[]): LogBlock[] {
    const blocks: LogBlock[] = []
    for (const line of lines) {
        const last = blocks[blocks.length - 1]
        if (line.continuation && last) {
            last.tail.push(line)
            last.level = severer(last.level, line.level)
        } else {
            blocks.push({head: line, tail: [], level: line.level})
        }
    }
    return blocks
}

export function atLeast(level: LogLevel | null, floor: LogLevel): boolean {
    return level !== null && SEVERITY[level] >= SEVERITY[floor]
}

export interface SearchQuery {
    text: string
    regex: boolean
    caseSensitive: boolean
}

/**
 * The pattern a query searches with, or null when there is nothing to search for or the regex does
 * not compile - which is said beside the box rather than thrown.
 */
export function patternOf(query: SearchQuery): RegExp | null {
    if (!query.text) return null
    const source = query.regex ? query.text : query.text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    try {
        return new RegExp(source, query.caseSensitive ? 'g' : 'gi')
    } catch {
        return null
    }
}

/** A piece of a line, marked when it is part of a match. */
export interface Fragment {
    text: string
    match: boolean
}

export function fragmentsOf(text: string, pattern: RegExp | null): Fragment[] {
    if (!pattern) return [{text, match: false}]
    const fragments: Fragment[] = []
    let last = 0
    pattern.lastIndex = 0
    for (const found of text.matchAll(pattern)) {
        if (found[0].length === 0) continue
        const start = found.index ?? 0
        if (start > last) fragments.push({text: text.slice(last, start), match: false})
        fragments.push({text: found[0], match: true})
        last = start + found[0].length
    }
    if (last < text.length || fragments.length === 0) fragments.push({text: text.slice(last), match: false})
    return fragments
}

/** The numbers of the lines a pattern matches. */
export function matchingLines(lines: LogLine[], pattern: RegExp | null): number[] {
    if (!pattern) return []
    return lines.filter(line => {
        pattern.lastIndex = 0
        return pattern.test(line.text)
    }).map(line => line.number)
}

/** Exceptions that read the same, counted. */
export interface ExceptionGroup {
    /** The first line: the exception and its message. */
    title: string
    /** The first frame, which is where it was thrown. */
    origin: string | null
    count: number
    /** The first of them in full. */
    sample: string
    /** The positions of the sections they came from. */
    positions: number[]
}

/**
 * Groups exceptions by what they say and where they were thrown, ignoring numbers in the message -
 * an entity id or a coordinate would otherwise make every occurrence of one bug its own group.
 */
export function groupExceptions(exceptions: {position: number, content: string}[]): ExceptionGroup[] {
    const groups = new Map<string, ExceptionGroup>()
    for (const {position, content} of exceptions) {
        const lines = content.split(/\r?\n/)
        const title = lines.find(line => line.trim().length > 0)?.trim() ?? ''
        const origin = lines.find(line => /^\s+at\s/.test(line))?.trim() ?? null
        const key = `${title.replace(/\d+/g, '#')}\n${origin ?? ''}`
        const group = groups.get(key)
        if (group) {
            group.count++
            group.positions.push(position)
        } else {
            groups.set(key, {title, origin, count: 1, sample: content, positions: [position]})
        }
    }
    return [...groups.values()].sort((a, b) => b.count - a.count)
}

export type TokenKind = 'key' | 'string' | 'number' | 'literal' | 'comment' | 'plain'

export interface Token {
    text: string
    kind: TokenKind
}

const YAML_KEY = /^(\s*(?:-\s+)?)([^\s#:][^#:]*?)(:)(?=\s|$)/
const YAML_SCALAR = /^(\s*)(".*"|'.*'|-?\d+(?:\.\d+)?|true|false|null|~)(\s*)$/i

function scalarKind(value: string): TokenKind {
    if (/^["']/.test(value)) return 'string'
    if (/^-?\d/.test(value)) return 'number'
    return 'literal'
}

function valueTokens(rest: string): Token[] {
    const hash = rest.search(/\s#/)
    const value = hash >= 0 ? rest.slice(0, hash) : rest
    const comment = hash >= 0 ? rest.slice(hash) : ''
    const scalar = YAML_SCALAR.exec(value)
    const tokens: Token[] = scalar
        ? [{text: scalar[1]!, kind: 'plain'}, {text: scalar[2]!, kind: scalarKind(scalar[2]!)}, {text: scalar[3]!, kind: 'plain'}]
        : [{text: value, kind: 'plain'}]
    if (comment) tokens.push({text: comment, kind: 'comment'})
    return tokens.filter(token => token.text.length > 0)
}

/**
 * Colours a line of a YAML configuration file: keys, quoted strings, numbers, booleans and comments.
 * Good enough to read a plugin's config by, not a parser - anything it does not recognise stays plain.
 */
export function yamlTokens(line: string): Token[] {
    if (/^\s*#/.test(line)) return [{text: line, kind: 'comment'}]
    const key = YAML_KEY.exec(line)
    if (!key) return valueTokens(line)
    return [
        {text: key[1]!, kind: 'plain' as TokenKind},
        {text: key[2]!, kind: 'key' as TokenKind},
        {text: key[3]!, kind: 'plain' as TokenKind},
        ...valueTokens(line.slice(key[0].length)),
    ].filter(token => token.text.length > 0)
}

/** A line of one of a report's sections, as a link names it. */
export interface LineTarget {
    position: number
    line: number
}
