/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {config, library} from '@fortawesome/fontawesome-svg-core'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'

import {
    faArrowLeft,
    faArrowRight,
    faCartShopping,
    faCheck,
    faCircleCheck,
    faChevronDown,
    faChevronRight,
    faCircleExclamation,
    faCircleHalfStroke,
    faCircleInfo,
    faCircleNotch,
    faCircleXmark,
    faDownload,
    faMagnifyingGlass,
    faMoon,
    faPen,
    faPlus,
    faSpinner,
    faSun,
    faTrash,
    faTriangleExclamation,
    faXmark,
} from '@fortawesome/free-solid-svg-icons'
import {faGithub} from '@fortawesome/free-brands-svg-icons'

config.autoAddCss = false

/**
 * Registers every icon the application is allowed to render.
 *
 * <p>One `library.add()` call per icon rather than one call carrying all of them: the bundled API
 * declares a signature wide enough that building the overload union for twenty arguments at once
 * overruns TypeScript's complexity budget.
 */
library.add(faArrowLeft)
library.add(faArrowRight)
library.add(faCartShopping)
library.add(faCheck)
library.add(faCircleCheck)
library.add(faChevronDown)
library.add(faChevronRight)
library.add(faCircleExclamation)
library.add(faCircleHalfStroke)
library.add(faCircleInfo)
library.add(faCircleNotch)
library.add(faCircleXmark)
library.add(faDownload)
library.add(faMagnifyingGlass)
library.add(faMoon)
library.add(faPen)
library.add(faPlus)
library.add(faSpinner)
library.add(faSun)
library.add(faTrash)
library.add(faTriangleExclamation)
library.add(faXmark)
library.add(faGithub)

/**
 * Registers the icon component globally.
 *
 * <p>`FontAwesomeIcon`'s own component type carries a deeply nested overload union that overruns
 * TypeScript's complexity budget at the call site, so it is widened before being handed over.
 */
export default defineNuxtPlugin((nuxtApp) => {
    nuxtApp.vueApp.component('font-awesome-icon', FontAwesomeIcon as unknown as object)
})
