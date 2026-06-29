<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {overview, type Overview} from '~/api/account'
import Spinner from '~/components/feedback/Spinner.vue'

definePageMeta({layout: 'account'})

const data = ref<Overview | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)

onMounted(async () => {
  try {
    data.value = await overview()
  } catch (e) {
    errorMessage.value = (e as Error).message ?? 'Failed to load account'
  } finally {
    loading.value = false
  }
})

function relativeTime(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}
</script>

<template>
  <div>
    <h1 class="mb-6 text-2xl font-bold">
      Account
    </h1>
    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <div v-else-if="errorMessage" class="rounded-theme border border-error/40 bg-error/10 p-4 text-error">
      {{ errorMessage }}
    </div>
    <div v-else-if="data" class="grid grid-cols-1 gap-4 md:grid-cols-2">
      <section class="rounded-theme border border-border-light dark:border-border-dark p-4">
        <h2 class="mb-3 text-sm font-semibold uppercase tracking-wider opacity-70">
          Linked accounts
        </h2>
        <div class="space-y-2 text-sm">
          <div>
            <div class="opacity-60">
              Email
            </div>
            <div>{{ data.account.email ?? 'Not set' }}</div>
          </div>
          <div>
            <div class="opacity-60">
              Discord
            </div>
            <div>
              <template v-if="data.account.discordId">
                Linked (id {{ data.account.discordId }})
              </template>
              <template v-else>
                Not linked — <a href="/api/auth/discord/start" class="text-primary hover:underline">link now</a>
              </template>
            </div>
          </div>
        </div>
      </section>
      <section class="rounded-theme border border-border-light dark:border-border-dark p-4">
        <h2 class="mb-3 text-sm font-semibold uppercase tracking-wider opacity-70">
          Sessions
        </h2>
        <div class="text-sm">
          <div class="text-3xl font-bold">
            {{ data.activeSessions }}
          </div>
          <div class="opacity-70">
            active · last sign-in {{ relativeTime(data.lastSignInAt) }}
          </div>
          <NuxtLink to="/account/security" class="mt-2 inline-block text-sm text-primary hover:underline">
            Manage sessions →
          </NuxtLink>
        </div>
      </section>
      <section class="rounded-theme border border-border-light dark:border-border-dark p-4 md:col-span-2">
        <h2 class="mb-3 text-sm font-semibold uppercase tracking-wider opacity-70">
          Recent downloads
        </h2>
        <div v-if="data.recentDownloads.length === 0" class="text-sm opacity-70">
          No downloads yet — browse the
          <NuxtLink to="/" class="text-primary hover:underline">
            storefront
          </NuxtLink>.
        </div>
        <ul v-else class="space-y-2 text-sm">
          <li v-for="row in data.recentDownloads" :key="row.id" class="flex items-center justify-between">
            <div>
              <div class="font-medium">
                {{ row.productName }} {{ row.version }}
              </div>
              <div class="text-xs opacity-60">
                {{ relativeTime(row.downloadedAt) }} · {{ row.source }}
              </div>
            </div>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>
