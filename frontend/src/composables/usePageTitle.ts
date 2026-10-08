/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type MaybeRefOrGetter, toValue} from 'vue'
import {useI18n} from 'vue-i18n'

/**
 * Names the page in the browser tab, followed by the site's name. A title that is not known yet -
 * a product still loading - shows the site's name alone until it is.
 *
 * <p>Every page calls this, or {@link useLinkPreview}, which does it too; {@code lint:page-titles}
 * fails a page that does neither.
 */
export function usePageTitle(title: MaybeRefOrGetter<string | null | undefined>) {
    const {t} = useI18n()
    useHead({
        title: () => {
            const page = toValue(title)
            return page ? `${page} · ${t('common.siteName')}` : t('common.siteName')
        },
    })
}
