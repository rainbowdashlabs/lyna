/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {defineEventHandler, proxyRequest} from 'h3'
import {useRuntimeConfig} from '#imports'

/**
 * Hands the paths the backend owns straight to it, at the address this server holds while it runs.
 *
 * <p>The route rule this replaces resolved its target while the application was being built, so an
 * image built without the backend address set proxied to the build machine's default for the rest
 * of its life, whatever its runtime environment said. Reading the address per request is what makes
 * one image usable in more than one place.
 *
 * <p>A redirect from the backend is passed to the browser rather than followed. Following it here
 * fetched Discord's sign-in page on the server and served it under this address, so the browser never
 * reached Discord and never came back.
 *
 * <p>Everything else falls through untouched: returning nothing from a middleware lets the request
 * carry on to the pages.
 */
export default defineEventHandler(event => {
    if (!event.path.startsWith('/api/')) return

    const {backendUrl} = useRuntimeConfig(event)
    return proxyRequest(event, `${backendUrl}${event.path}`, {fetchOptions: {redirect: 'manual'}})
})
