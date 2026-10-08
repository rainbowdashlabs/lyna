/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type MaybeRefOrGetter, toValue} from 'vue'
import {useI18n} from 'vue-i18n'
import {discordEmbed, type EmbedCard} from '~/util/discordEmbed'
import {usePageTitle} from './usePageTitle'

export interface LinkPreview {
    title: string
    description: string
    /** Absolute, or a path on this site. */
    image: string | null
    /** A Discord component embed, shown instead of the OpenGraph card where Discord supports it. */
    card?: EmbedCard
}

/**
 * What a shared link to this page shows: the title in the tab, OpenGraph and Twitter tags for every
 * site that reads them, and a Discord component embed where there is one. Only useful on a page
 * rendered on the server, since that is all a crawler sees.
 */
export function useLinkPreview(preview: MaybeRefOrGetter<LinkPreview | null>) {
    const siteName = useI18n().t('common.siteName')
    const origin = useRequestURL().origin
    const url = useRequestURL().href
    const absolute = (path: string | null) => !path ? null : path.startsWith('http') ? path : `${origin}${path}`
    const current = computed(() => toValue(preview))

    usePageTitle(() => current.value?.title)
    useHead(() => {
        const value = current.value
        if (!value) return {}
        const image = absolute(value.image)
        return {
            meta: [
                {property: 'og:type', content: 'website'},
                {property: 'og:site_name', content: siteName},
                {property: 'og:title', content: value.title},
                {property: 'og:description', content: value.description},
                {property: 'og:url', content: url},
                ...(image ? [{property: 'og:image', content: image}] : []),
                {name: 'twitter:card', content: 'summary'},
                {name: 'description', content: value.description},
                {name: 'theme-color', content: '#eb9db2'},
            ],
            script: value.card
                ? [{id: 'discord:component-embed', type: 'application/json', innerHTML: discordEmbed({
                    ...value.card,
                    thumbnail: absolute(value.card.thumbnail),
                    buttons: value.card.buttons.map(button => ({...button, url: absolute(button.url)!})),
                })}]
                : [],
        }
    })
}
