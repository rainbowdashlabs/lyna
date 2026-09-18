/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'

/**
 * Demo mode, from the outside of an instance that is not one.
 *
 * <p>This stack runs with demo mode off, which is the state that matters most: signing in there
 * needs no password, so the endpoints have to be shut, and shut by the backend rather than by a page
 * that declines to show a button. A regression here would not look like a broken feature - it would
 * look like nothing at all, until somebody signed in as an operator.
 */
test.describe('Demo mode, switched off', () => {
    test.skip(process.env.LYNA_E2E_DEMO === 'true', 'the stack was started with demo mode on')

    test('the account list is not there', async ({request}) => {
        const response = await request.get('/api/v1/demo/accounts')

        // Not found rather than forbidden: an instance that is not a demo does not advertise that it
        // could be one.
        expect(response.status()).toBe(404)
    })

    test('signing in without a password is not there', async ({request}) => {
        const response = await request.post('/api/v1/demo/login', {
            data: {email: 'demo-operator@example.invalid'},
        })

        expect(response.status()).toBe(404)
    })

    test('the reset is not there', async ({request}) => {
        const response = await request.post('/api/v1/demo/reset')

        expect(response.status()).toBe(404)
    })

    test('no page offers to sign somebody in', async ({page}) => {
        await page.goto('/login')

        await expect(page.getByText('Sign in as')).toHaveCount(0)
        await expect(page.getByText(/This is a demo/i)).toHaveCount(0)
    })

    test('the storefront carries no demo banner', async ({page}) => {
        await page.goto('/')

        await expect(page.getByRole('status')).toHaveCount(0)
    })
})

/**
 * The same instance with demo mode on.
 *
 * <p>Skipped unless the stack was started with it - `LYNA_E2E_DEMO=true ./toolchain.sh docker-e2e` -
 * because the mode is decided when the backend starts and one run cannot have it both ways.
 *
 * <p>The stack has no bot, so nothing can be seeded. That is the interesting case for these: demo
 * mode being on must still not be a way to sign in as somebody, and an empty seed must not be a way
 * to sign in as nobody.
 */
test.describe('Demo mode, switched on', () => {
    test.skip(process.env.LYNA_E2E_DEMO !== 'true', 'the stack was started with demo mode off')

    test('the account list is offered, and is empty until something is seeded', async ({request}) => {
        const response = await request.get('/api/v1/demo/accounts')

        expect(response.ok()).toBe(true)
        expect(await response.json()).toEqual([])
    })

    test('an account the seed did not make cannot be signed in as', async ({request}) => {
        const response = await request.post('/api/v1/demo/login', {
            data: {email: 'demo-operator@example.invalid'},
        })

        expect(response.status()).toBe(401)
    })

    test('an account somebody made themselves cannot be signed in as either', async ({request}) => {
        const email = `real-${Date.now()}@example.invalid`
        await request.post('/api/auth/signup', {data: {email, password: 'end-to-end-password'}})

        const response = await request.post('/api/v1/demo/login', {data: {email}})

        expect(response.status()).toBe(401)
    })

    test('a reset that cannot seed says why rather than half-doing it', async ({request}) => {
        const response = await request.post('/api/v1/demo/reset')

        expect(response.status()).toBe(503)
        expect(await response.text()).toContain('guild')
    })
})
