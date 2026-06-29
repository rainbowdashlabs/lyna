/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {config, library, type IconDefinition} from '@fortawesome/fontawesome-svg-core'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'

import {faArrowLeft} from '@fortawesome/free-solid-svg-icons/faArrowLeft'
import {faArrowRight} from '@fortawesome/free-solid-svg-icons/faArrowRight'
import {faCartShopping} from '@fortawesome/free-solid-svg-icons/faCartShopping'
import {faCheck} from '@fortawesome/free-solid-svg-icons/faCheck'
import {faCircleHalfStroke} from '@fortawesome/free-solid-svg-icons/faCircleHalfStroke'
import {faCircleExclamation} from '@fortawesome/free-solid-svg-icons/faCircleExclamation'
import {faCircleInfo} from '@fortawesome/free-solid-svg-icons/faCircleInfo'
import {faCircleNotch} from '@fortawesome/free-solid-svg-icons/faCircleNotch'
import {faCircleXmark} from '@fortawesome/free-solid-svg-icons/faCircleXmark'
import {faDownload} from '@fortawesome/free-solid-svg-icons/faDownload'
import {faMoon} from '@fortawesome/free-solid-svg-icons/faMoon'
import {faSpinner} from '@fortawesome/free-solid-svg-icons/faSpinner'
import {faSun} from '@fortawesome/free-solid-svg-icons/faSun'
import {faTriangleExclamation} from '@fortawesome/free-solid-svg-icons/faTriangleExclamation'
import {faXmark} from '@fortawesome/free-solid-svg-icons/faXmark'
import {faGithub} from '@fortawesome/free-brands-svg-icons/faGithub'

config.autoAddCss = false

const icons: IconDefinition[] = [
    faArrowLeft,
    faArrowRight,
    faCartShopping,
    faCheck,
    faCircleHalfStroke,
    faCircleExclamation,
    faCircleInfo,
    faCircleNotch,
    faCircleXmark,
    faDownload,
    faMoon,
    faSpinner,
    faSun,
    faTriangleExclamation,
    faXmark,
    faGithub,
]
for (const icon of icons) library.add(icon)

export default defineNuxtPlugin((nuxtApp) => {
    // FontAwesomeIcon's component type contains a deeply nested overload union
    // that overruns TS's complexity budget; cast keeps the call site sane.
    nuxtApp.vueApp.component('font-awesome-icon', FontAwesomeIcon as unknown as object)
})
