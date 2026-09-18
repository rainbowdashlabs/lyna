export default defineNuxtConfig({
    compatibilityDate: '2026-06-29',

    srcDir: 'src/',

    css: ['~/style.css'],

    runtimeConfig: {
        // Where this server reaches the backend. Read per request by server/middleware/backend-proxy,
        // so one built image serves whatever address its environment names.
        backendUrl: process.env.NUXT_BACKEND_URL || 'http://localhost:8888',
    },

    modules: ['@nuxtjs/i18n'],

    i18n: {
        restructureDir: 'src/i18n',
        defaultLocale: 'en-US',
        strategy: 'no_prefix',
        locales: [{code: 'en-US', language: 'en-US', file: 'en-US.json'}],
    },

    routeRules: {
        '/': {ssr: true},
        '/products/**': {ssr: true},
        '/login': {ssr: true},
        '/signup': {ssr: true},
        '/forgot-password': {ssr: true},
        '/reset-password': {ssr: true},
        '/account': {ssr: false},
        '/account/**': {ssr: false},
        '/admin': {ssr: false},
        '/admin/**': {ssr: false},
    },

    nitro: {
        // Bundled into the server rather than left external, so the server and the browser share one
        // icon library. Kept apart, the registration the plugin performs reaches only one of them and
        // every icon renders empty on the first paint.
        plugins: ['../server/plugins/theme-script.ts'],
        externals: {
            inline: [
                '@fortawesome/fontawesome-svg-core',
                '@fortawesome/free-brands-svg-icons',
                '@fortawesome/free-solid-svg-icons',
                '@fortawesome/vue-fontawesome',
            ],
        },
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
