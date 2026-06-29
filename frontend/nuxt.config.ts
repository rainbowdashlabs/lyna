export default defineNuxtConfig({
    compatibilityDate: '2026-06-29',

    srcDir: 'src/',

    css: ['~/style.css'],

    routeRules: {
        '/': {ssr: true},
        '/login': {ssr: true},
        '/signup': {ssr: true},
        '/forgot-password': {ssr: true},
        '/reset-password': {ssr: true},
        '/account': {ssr: false},
        '/account/**': {ssr: false},
        '/admin': {ssr: false},
        '/admin/**': {ssr: false},
        '/api/**': {proxy: `${process.env.NUXT_BACKEND_URL || 'http://localhost:8888'}/api/**`},
    },

    nitro: {
        devProxy: {
            '/api': {target: process.env.NUXT_BACKEND_URL || 'http://localhost:8888', changeOrigin: true},
        },
    },

    vite: {
        server: {
            allowedHosts: true,
            proxy: {
                '/api': process.env.NUXT_BACKEND_URL || 'http://localhost:8888',
            },
        },
    },

    hooks: {
        'vite:extendConfig'(config: { plugins?: unknown[] }) {
            import('@tailwindcss/vite').then(m => {
                config.plugins ||= []
                config.plugins.push(m.default())
            })
        },
    },

    typescript: {
        strict: true,
    },

    components: {
        dirs: [
            {path: '~/components', pathPrefix: false},
        ],
    },
})
