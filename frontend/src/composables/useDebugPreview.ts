/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {DebugReport} from '~/api/debug'
import {formatDate} from '~/util/format'
import {useLinkPreview} from './useLinkPreview'
import {useServerFetch} from './useServerFetch'

/**
 * What a debug report's link shows when it is pasted into a support channel: which plugin, which
 * version, which server, and how many exceptions it carries. Nothing from the logs themselves.
 */
export function useDebugPreview(readKey: string) {
    const {t} = useI18n()
    const {data: report} = useServerFetch<DebugReport>(`preview-debug-${readKey}`, `/api/v1/debug/${encodeURIComponent(readKey)}`)

    useLinkPreview(computed(() => {
        const value = report.value
        if (!value) return {title: t('preview.debug.missing'), description: t('preview.debug.missing'), image: null}
        const exceptions = value.sections.filter(section => section.kind.endsWith('_EXCEPTION')).length
        const configs = value.sections.filter(section => section.kind === 'CONFIG').length
        const description = t('preview.debug.description', {
            server: value.serverMeta.version ?? '-',
            exceptions,
            configs,
            expires: formatDate(value.expires),
        })
        const title = t('preview.debug.title', {plugin: value.pluginName, version: value.pluginVersion})
        return {
            title,
            description,
            image: null,
            card: {
                heading: `## ${title}`,
                body: description,
                thumbnail: null,
                buttons: [{label: t('preview.debug.open'), url: `/debug/v1/read/${readKey}`}],
            },
        }
    }))
}
