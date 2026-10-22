/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useI18n} from 'vue-i18n'

/**
 * Says a range of versions in words: both ends, an open end, or nothing when there is no bound at
 * all. Used for Minecraft versions and for the versions of a dependency.
 */
export function useVersionRange() {
    const {t} = useI18n()
    return (min: string | null | undefined, max: string | null | undefined): string | null => {
        if (min && max) return t('common.manifest.range.between', {min, max})
        if (min) return t('common.manifest.range.from', {min})
        if (max) return t('common.manifest.range.upTo', {max})
        return null
    }
}
