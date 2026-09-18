/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {createI18n} from 'vue-i18n'
import en from './locales/en-US.json'

export const i18n = createI18n({
    legacy: false,
    locale: 'en-US',
    fallbackLocale: 'en-US',
    messages: {
        'en-US': en,
    },
    missingWarn: false,
    fallbackWarn: false,
})
