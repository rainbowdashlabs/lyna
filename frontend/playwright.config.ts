/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {defineConfig, devices} from '@playwright/test'

/**
 * The end-to-end suite. It walks the real application against a real backend and database, which is
 * why it is the only thing under `frontend/` allowed to reach above that directory: the backend it
 * needs is started from `docker/`. Nothing in the lint chain or the container image reads this file.
 *
 * The `ssr-no-js` project is not a nicety. Public routes are server-rendered by route rule, and a
 * context with JavaScript switched off is the only way to assert that they really are rather than
 * being repaired by hydration.
 *
 * The stack runs on ports of its own, so a run never touches the dev stack a developer has open.
 * `toolchain.sh` derives them from the checkout's path and exports them, which is what lets several
 * checkouts run the suite at once without dividing the ports between them by hand; the defaults
 * below are what a bare `npx playwright test` gets.
 */
const backendUrl = process.env.NUXT_BACKEND_URL || 'http://localhost:8890'
const baseUrl = process.env.E2E_BASE_URL || 'http://localhost:3011'
const webPort = new URL(baseUrl).port || '3000'

export default defineConfig({
    testDir: './e2e',
    outputDir: './e2e/results',
    // Specs are named after their feature, not `*.spec.ts`, so the default pattern would find none.
    testMatch: /.*\.e2e\.ts/,
    timeout: 60_000,
    expect: {timeout: 15_000},
    retries: process.env.CI ? 2 : 0,
    /**
     * Four against the built server it normally uses, two against a dev server, which compiles each
     * route on demand from a single process and falls behind under more.
     */
    workers: process.env.E2E_DEV_SERVER ? 2 : 4,
    fullyParallel: true,
    reporter: process.env.CI ? [['html', {outputFolder: 'e2e/report'}], ['list']] : 'list',

    use: {
        baseURL: baseUrl,
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
        video: 'on-first-retry',
    },

    projects: [
        {name: 'chromium', testIgnore: /.*\.ssr\.e2e\.ts/, use: {...devices['Desktop Chrome']}},
        {name: 'firefox', testIgnore: /.*\.ssr\.e2e\.ts/, use: {...devices['Desktop Firefox']}},
        {name: 'mobile', testIgnore: /.*\.ssr\.e2e\.ts/, use: {...devices['iPhone 14']}},
        {
            name: 'ssr-no-js',
            testMatch: /.*\.ssr\.e2e\.ts/,
            use: {...devices['Desktop Chrome'], javaScriptEnabled: false},
        },
    ],

    /**
     * A stack of its own: the database and backend come from the `e2e` compose profile, and the Nuxt
     * server runs from this checkout. Every story signs up its own account under a name of its own,
     * so nothing has to be reset between runs and two runs never collide.
     *
     * The frontend is the built server rather than a second dev server, for two reasons: Nuxt allows
     * only one dev server per project, so a suite that wanted its own would fight whoever is
     * working; and a dev server compiles each route the first time it is asked for, which the suite
     * outgrew. Set E2E_DEV_SERVER to use one anyway while writing a single story.
     */
    webServer: process.env.E2E_NO_SERVER
        ? undefined
        : [
            {
                // In the foreground, without -d: a command that returns straight away is taken for a
                // server that died, and the containers it started in the background go unnoticed -
                // the stack is still building the backend at that point. Staying attached also means
                // the stack goes down with the run that brought it up.
                command: 'docker compose -f ../docker/docker-compose.yml --profile e2e up',
                url: `${backendUrl}/api/v1/products`,
                reuseExistingServer: true,
                // The backend is built inside its container from the sources beside it. On a machine
                // that has done it before this is a moment; on a cold one - a fresh runner with no
                // Gradle cache - it is the whole build, so the wait is generous.
                timeout: 900_000,
            },
            !process.env.E2E_DEV_SERVER
                ? {
                    // Named in the command rather than handed over as an environment, which does not
                    // always reach the process: without the address the server falls back to its
                    // default backend and every proxied call answers 500.
                    command: `NUXT_BACKEND_URL=${backendUrl} NITRO_PORT=${webPort} node .output/server/index.mjs`,
                    url: baseUrl,
                    reuseExistingServer: !process.env.CI,
                    timeout: 120_000,
                }
                : {
                    command: `NUXT_BACKEND_URL=${backendUrl} npm run dev -- --port ${webPort}`,
                    url: baseUrl,
                    reuseExistingServer: !process.env.CI,
                    timeout: 120_000,
                },
        ],
})
