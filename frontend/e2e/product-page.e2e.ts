/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext, expect, test} from '@playwright/test'
import {PASSWORD} from './fixtures/auth'

/**
 * The page a product has of its own.
 *
 * <p>Reached from the storefront, and carrying the description an operator wrote. The description is
 * markdown, which means it is also the one place where what an operator types becomes markup, so
 * what it refuses to render is as much the point as what it renders.
 */
test.describe('A product page', () => {
    test('is reached by the name on its tile', async ({page}) => {
        await page.goto('/')

        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('link', {name: 'E2E Freebie', exact: true}).click()

        await expect(page).toHaveURL(/\/products\/\d+$/)
        await expect(page.getByRole('heading', {level: 1, name: 'E2E Freebie'})).toBeVisible()
    })

    test('renders the description as markdown rather than as its source', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('link', {name: 'E2E Freebie', exact: true}).click()

        await expect(page.getByRole('heading', {name: 'What it does'})).toBeVisible()
        await expect(page.locator('.prose-description strong')).toHaveText('freebie')
        await expect(page.locator('.prose-description li')).toHaveCount(2)
        await expect(page.getByText('# What it does')).toHaveCount(0)
    })

    test('does not let an operator publish script through the description', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('link', {name: 'E2E Freebie', exact: true}).click()
        await expect(page.locator('.prose-description')).toBeVisible()

        const attribute = await page.locator('.prose-description img').first()
            .getAttribute('onerror')
            .catch(() => null)
        expect(attribute).toBeNull()
        expect(await page.evaluate(() => (window as unknown as {__xss?: number}).__xss)).toBeUndefined()
    })

    test('offers the download a free product offers on its tile', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('link', {name: 'E2E Freebie', exact: true}).click()

        await expect(page.getByRole('button', {name: 'Download'})).toBeVisible()
    })

    test('a premium product offers the purchase instead', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Premium'})
            .getByRole('link', {name: 'E2E Premium', exact: true}).click()

        await expect(page.getByRole('link', {name: /Buy on Ko-fi/})).toBeVisible()
        await expect(page.getByRole('button', {name: 'Download'})).toHaveCount(0)
    })

    test('a product nobody has written about says so', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Premium'})
            .getByRole('link', {name: 'E2E Premium', exact: true}).click()

        await expect(page.getByText('Nothing has been written about this one yet.')).toBeVisible()
    })

    test('a product that does not exist says so rather than failing', async ({page}) => {
        await page.goto('/products/999999')

        await expect(page.getByText('There is no such product.')).toBeVisible()
    })
})

/**
 * The footer, which is where somebody reading a page can find out what they are looking at.
 */
test.describe('The footer', () => {
    test('says which build the instance is running', async ({page}) => {
        await page.goto('/')

        const footer = page.getByRole('contentinfo')
        await expect(footer).toBeVisible()
        await expect(footer.getByText(/^\d+\.\d+/)).toBeVisible()
    })

    test('links to the source and carries the licence', async ({page}) => {
        await page.goto('/')

        const footer = page.getByRole('contentinfo')
        await expect(footer.getByRole('link', {name: 'Source'})).toHaveAttribute(
            'href', 'https://github.com/rainbowdashlabs/lyna')
        await expect(footer.getByText('AGPL-3.0-only')).toBeVisible()
    })

    test('offers the Discord the instance was configured with', async ({page}) => {
        await page.goto('/')

        await expect(page.getByRole('contentinfo').getByRole('link', {name: 'Add the bot'})).toBeVisible()
    })

    test('is on the product page too', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('link', {name: 'E2E Freebie', exact: true}).click()

        await expect(page.getByRole('contentinfo')).toBeVisible()
    })
})

/**
 * Uploading an icon, and editing a product at all.
 *
 * <p>Driven as the seeded operator, so what is pinned is the round trip: an upload comes back out as
 * an image at the size a page asks for, and an edit changes what the storefront then says. The
 * refusals matter too - an upload that is not an image, and anybody who administers nothing.
 */
test.describe('A product an operator administers', () => {
    /** A real PNG, small enough to write out here. */
    const PNG = Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
        'base64')

    /** The seeded account is named as an operator by the Discord id it is linked to. */
    async function operator(request: APIRequestContext): Promise<Record<string, string>> {
        const login = await request.post('/api/auth/login', {
            data: {email: 'entitled@example.invalid', password: PASSWORD},
        })
        const {token} = await login.json()
        return {Authorization: `Bearer ${token}`}
    }

    async function productNamed(request: APIRequestContext, name: string) {
        const products = await (await request.get('/api/v1/products')).json()
        return products.find((p: { name: string }) => p.name === name)
    }

    test('an uploaded icon is served back as an image', async ({request}) => {
        const headers = await operator(request)
        const product = await productNamed(request, 'E2E Freebie')

        const upload = await request.post(`/api/admin/g/4242/products/${product.id}/icon`, {
            headers,
            multipart: {icon: {name: 'icon.png', mimeType: 'image/png', buffer: PNG}},
        })
        expect(upload.status()).toBe(204)

        const served = await request.get(`/api/v1/products/${product.id}/icon?size=64`)
        expect(served.status()).toBe(200)
        expect(served.headers()['content-type']).toContain('image/')
        expect((await served.body()).length).toBeGreaterThan(0)
    })

    test('the catalogue then points at the icon this instance serves', async ({request}) => {
        const headers = await operator(request)
        const product = await productNamed(request, 'E2E Freebie')
        await request.post(`/api/admin/g/4242/products/${product.id}/icon`, {
            headers,
            multipart: {icon: {name: 'icon.png', mimeType: 'image/png', buffer: PNG}},
        })

        const again = await productNamed(request, 'E2E Freebie')
        expect(again.iconUrl).toBe(`/api/v1/products/${product.id}/icon`)
    })

    test('a removed icon is gone, and the product says it has none', async ({request}) => {
        const headers = await operator(request)
        const product = await productNamed(request, 'E2E Freebie')
        await request.post(`/api/admin/g/4242/products/${product.id}/icon`, {
            headers,
            multipart: {icon: {name: 'icon.png', mimeType: 'image/png', buffer: PNG}},
        })

        expect((await request.delete(`/api/admin/g/4242/products/${product.id}/icon`, {headers})).status())
            .toBe(204)
        expect((await request.get(`/api/v1/products/${product.id}/icon`)).status()).toBe(404)
    })

    test('a file that is not an image is refused', async ({request}) => {
        const headers = await operator(request)
        const product = await productNamed(request, 'E2E Freebie')

        const upload = await request.post(`/api/admin/g/4242/products/${product.id}/icon`, {
            headers,
            multipart: {icon: {name: 'icon.png', mimeType: 'image/png', buffer: Buffer.from('not an image')}},
        })
        expect(upload.status()).toBe(400)
        expect(await upload.text()).toContain('PNG, JPEG or WebP')
    })

    test('a product with no icon answers that it has none rather than failing', async ({request}) => {
        const product = await productNamed(request, 'E2E Premium')

        expect((await request.get(`/api/v1/products/${product.id}/icon`)).status()).toBe(404)
    })

    test('everything about a product can be changed in one save', async ({request}) => {
        const headers = await operator(request)
        const product = await productNamed(request, 'E2E Unsellable')

        const saved = await request.put(`/api/admin/g/4242/products/${product.id}`, {
            headers,
            data: {
                name: 'E2E Unsellable',
                url: 'https://example.invalid/edited',
                roleId: '3',
                free: false,
                trial: true,
                description: '# Edited\n\nBy the **operator**.',
                iconUrl: null,
            },
        })
        expect(saved.status()).toBe(204)

        const detail = await (await request.get(`/api/v1/products/${product.id}`)).json()
        expect(detail.url).toBe('https://example.invalid/edited')
        expect(detail.description).toContain('Edited')
    })

    test('editing is refused to somebody who administers nothing', async ({request}) => {
        const product = await productNamed(request, 'E2E Freebie')

        const response = await request.put(`/api/admin/g/4242/products/${product.id}`, {
            data: {name: 'Renamed', url: null, roleId: '1', free: true, trial: false, description: null, iconUrl: null},
        })
        expect([401, 403]).toContain(response.status())
    })

    test('uploading is refused to somebody who administers nothing', async ({request}) => {
        const product = await productNamed(request, 'E2E Freebie')

        const upload = await request.post(`/api/admin/g/4242/products/${product.id}/icon`, {
            multipart: {icon: {name: 'icon.png', mimeType: 'image/png', buffer: PNG}},
        })
        expect([401, 403]).toContain(upload.status())
    })
})
