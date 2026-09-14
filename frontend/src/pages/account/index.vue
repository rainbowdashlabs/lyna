<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {overview, type Overview} from '~/api/account'

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
</script>

<template>
  <div>
    <PageHeader class="mb-6">
      Account
    </PageHeader>
    <AsyncSection :error="errorMessage ?? undefined" :loading="loading">
      <div v-if="data" class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <LinkedAccountsCard :account="data.account"/>
        <SessionsCard :active-sessions="data.activeSessions" :last-sign-in-at="data.lastSignInAt"/>
        <RecentDownloadsCard :rows="data.recentDownloads"/>
      </div>
    </AsyncSection>
  </div>
</template>
