/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, reactive, type Ref} from 'vue'
import type {DebugReport, DebugSection, DebugTab} from '~/api/debug'
import {matchingLines, patternOf, type SearchQuery, splitLines} from '~/util/debugLog'

/**
 * One search over a whole report, and how many matches each part of it holds.
 *
 * <p>Counts are taken over what has been fetched; the page fetches everything once a search starts,
 * so they settle as the sections arrive.
 */
export function useDebugSearch(report: Ref<DebugReport | null>, contents: Map<number, string>) {
    const query = reactive<SearchQuery>({text: '', regex: false, caseSensitive: false})
    const pattern = computed(() => patternOf(query))
    const invalid = computed(() => query.text.length > 0 && pattern.value === null)

    function test(text: string) {
        const current = pattern.value!
        current.lastIndex = 0
        return current.test(text)
    }

    function sectionsMatching(sections: DebugSection[]) {
        return sections.filter(section => {
            const content = contents.get(section.position)
            return content !== undefined && test(content)
        }).length
    }

    const counts = computed<Partial<Record<DebugTab, number>>>(() => {
        if (!pattern.value || !report.value) return {}
        const sections = report.value.sections
        const plugins = report.value.serverMeta.plugins ?? []
        return {
            logs: sections
                .filter(section => section.kind === 'LOG' || section.kind === 'PLUGIN_LOG')
                .reduce((sum, section) => sum + matchingLines(splitLines(contents.get(section.position) ?? ''), pattern.value).length, 0),
            exceptions: sectionsMatching(sections.filter(section => section.kind.endsWith('_EXCEPTION'))),
            configs: sectionsMatching(sections.filter(section => section.kind === 'CONFIG')),
            plugins: plugins.filter(plugin => test([plugin.name, plugin.version, ...(plugin.authors ?? [])].join(' '))).length,
        }
    })

    return {query, pattern, invalid, counts}
}
