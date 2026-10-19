/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {createHmac} from 'node:crypto'
import {type APIRequestContext, expect, test} from '@playwright/test'
import {createProduct, deleteProduct, GUILD, operatorHeaders, uniqueName} from './fixtures/admin'

/**
 * What a product's GitHub webhook answers to a repository's goings-on. The stack has no bot, so
 * nothing reaches Discord; the answer says whether it would have been posted.
 */
test.describe('A product\'s GitHub webhook', () => {
    const push = (branch: string) => JSON.stringify({
        ref: `refs/heads/${branch}`, deleted: false, compare: 'https://github.com/x/y/compare/a...b',
        commits: [{id: 'abcdef0123', message: 'Fix the moon', url: 'https://github.com/x/y/commit/abcdef0', author: {name: 'Ada'}}],
        repository: {full_name: 'x/y', default_branch: 'main'},
    })
    const pullRequest = (action: string, merged: boolean) => JSON.stringify({
        action, number: 7,
        pull_request: {title: 'Spook harder', html_url: 'https://github.com/x/y/pull/7', body: '', merged, user: {login: 'ada'}},
        repository: {full_name: 'x/y'},
    })
    const issue = (action: string) => JSON.stringify({
        action, issue: {number: 9, title: 'Moon too bright', html_url: 'https://github.com/x/y/issues/9', user: {login: 'ada'}},
        repository: {full_name: 'x/y'},
    })

    async function webhook(request: APIRequestContext, productId: number): Promise<{token: string, secret: string}> {
        const issued = await request.post(`/api/admin/g/${GUILD}/products/${productId}/release-webhook`, {
            headers: await operatorHeaders(request),
        })
        expect(issued.status()).toBe(200)
        const {url, secret} = await issued.json()
        return {token: url.split('/').pop(), secret}
    }

    async function deliver(request: APIRequestContext, hook: {token: string, secret: string}, event: string, body: string) {
        const signature = 'sha256=' + createHmac('sha256', hook.secret).update(body).digest('hex')
        return request.post(`/api/v1/webhook/github/${hook.token}`, {
            headers: {'X-GitHub-Event': event, 'X-Hub-Signature-256': signature, 'Content-Type': 'application/json'},
            data: body,
        })
    }

    test('posts pushes to the default branch, opened and merged PRs, and opened issues', async ({request}) => {
        const name = uniqueName('Active')
        const id = await createProduct(request, name)
        const hook = await webhook(request, id)

        expect((await deliver(request, hook, 'push', push('main'))).status()).toBe(200)
        expect((await deliver(request, hook, 'push', push('feature/x'))).status()).toBe(204)
        expect((await deliver(request, hook, 'pull_request', pullRequest('opened', false))).status()).toBe(200)
        expect((await deliver(request, hook, 'pull_request', pullRequest('closed', true))).status()).toBe(200)
        expect((await deliver(request, hook, 'pull_request', pullRequest('closed', false))).status()).toBe(204)
        expect((await deliver(request, hook, 'issues', issue('opened'))).status()).toBe(200)
        expect((await deliver(request, hook, 'issues', issue('closed'))).status()).toBe(200)

        await deleteProduct(request, id, name)
    })

    test('drops what the product switched off, and refuses what is not signed', async ({request}) => {
        const name = uniqueName('Quiet')
        const id = await createProduct(request, name)
        const hook = await webhook(request, id)
        const saved = await request.put(`/api/admin/g/${GUILD}/products/${id}/release-webhook/activity`, {
            headers: await operatorHeaders(request),
            data: {channelId: null, push: false, prOpened: true, prMerged: true, issueOpened: false, issueClosed: true},
        })
        expect(saved.status()).toBe(204)

        expect((await deliver(request, hook, 'push', push('main'))).status()).toBe(204)
        expect((await deliver(request, hook, 'issues', issue('opened'))).status()).toBe(204)
        expect((await deliver(request, hook, 'issues', issue('closed'))).status()).toBe(200)
        expect((await deliver(request, {...hook, secret: 'wrong'}, 'issues', issue('closed'))).status()).toBe(401)

        const read = await (await request.get(`/api/admin/g/${GUILD}/products/${id}/release-webhook`, {
            headers: await operatorHeaders(request),
        })).json()
        expect(read.activity).toEqual({push: false, prOpened: true, prMerged: true, issueOpened: false, issueClosed: true})

        await deleteProduct(request, id, name)
    })
})
