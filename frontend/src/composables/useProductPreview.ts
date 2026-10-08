/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {KioskProductDetail, VersionEntry} from '~/api/kiosk'
import {discordMarkdown, plainText} from '~/util/discordEmbed'
import {useLinkPreview} from './useLinkPreview'
import {useServerFetch} from './useServerFetch'

/**
 * What a link to a product's page shows in Discord and elsewhere: its name, its newest version,
 * whether it is free, what it says about itself, its icon, and buttons to download it or open its
 * source.
 */
export function useProductPreview(productId: number) {
    const {t} = useI18n()
    const origin = useRequestURL().origin
    const {data: product} = useServerFetch<KioskProductDetail>(`preview-product-${productId}`, `/api/v1/products/${productId}`)
    const {data: versions} = useServerFetch<VersionEntry[]>(
        `preview-versions-${productId}`, `/api/v1/products/${productId}/release-types/STABLE/versions?limit=1`)

    useLinkPreview(computed(() => {
        const value = product.value
        if (!value) return null
        const version = versions.value?.[0]?.version
        const description = plainText(value.description) || t('preview.product.noDescription', {name: value.name})
        const facts = [version ? `**${version}**` : null, value.free ? t('preview.product.free') : t('preview.product.premium')]
            .filter(Boolean)
            .join(' · ')
        const image = value.iconUrl ? `${value.iconUrl}${value.iconUrl.includes('?') ? '&' : '?'}size=256` : null
        return {
            title: value.name,
            description,
            image,
            card: {
                heading: `## ${value.name}\n${facts}`,
                body: discordMarkdown(value.description, `${origin}/products/${value.id}`) || description,
                thumbnail: image,
                buttons: [
                    {label: value.free ? t('preview.product.download') : t('preview.product.view'), url: `/products/${value.id}`},
                    ...(value.url ? [{label: t('preview.product.source'), url: value.url}] : []),
                ],
            },
        }
    }))
}
