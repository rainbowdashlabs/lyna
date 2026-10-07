/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, reactive, ref} from 'vue'
import {type DebugReport, type DebugSection, getDebugReport, getDebugSection, type SectionKind} from '~/api/debug'

/**
 * A debug report and the contents of its sections, fetched as they are needed.
 *
 * <p>A section is fetched once and kept. Searching needs every section, so {@link loadAll} fetches
 * whatever is still missing; until then only what was opened has been downloaded, which matters for
 * a server log of several megabytes.
 */
export function useDebugReport(readKey: string) {
    const report = ref<DebugReport | null>(null)
    const contents = reactive(new Map<number, string>())
    const loading = ref(true)
    const notFound = ref(false)
    const error = ref('')

    async function load() {
        try {
            report.value = await getDebugReport(readKey)
        } catch (e) {
            const status = (e as { response?: { status?: number } }).response?.status
            if (status === 404) notFound.value = true
            else error.value = (e as Error).message
        } finally {
            loading.value = false
        }
    }

    async function content(position: number): Promise<string> {
        const known = contents.get(position)
        if (known !== undefined) return known
        const fetched = await getDebugSection(readKey, position)
        contents.set(position, fetched)
        return fetched
    }

    async function loadAll() {
        await Promise.all((report.value?.sections ?? []).map(section => content(section.position)))
    }

    function ofKind(...kinds: SectionKind[]): DebugSection[] {
        return (report.value?.sections ?? []).filter(section => kinds.includes(section.kind))
    }

    const logs = computed(() => ofKind('LOG', 'PLUGIN_LOG'))
    const exceptions = computed(() => ofKind('INTERNAL_EXCEPTION', 'EXTERNAL_EXCEPTION'))
    const configs = computed(() => ofKind('CONFIG'))
    const metas = computed(() => ofKind('META'))

    return {report, contents, loading, notFound, error, load, content, loadAll, logs, exceptions, configs, metas}
}
