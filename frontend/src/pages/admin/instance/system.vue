<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {getInstanceSystem, type SystemInfo} from '~/api/admin'
import Spinner from '~/components/feedback/Spinner.vue'

definePageMeta({layout: 'admin'})

const info = ref<SystemInfo | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)

onMounted(async () => {
  try {
    info.value = await getInstanceSystem()
  } catch (e) {
    const error = e as {response?: {status?: number}}
    errorMessage.value = error.response?.status === 404 ? 'Operator access required.' : 'Failed to load system info.'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <PageHeader class="mb-4">
      System
    </PageHeader>
    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <div v-else-if="errorMessage" class="rounded-theme border border-error/40 bg-error/10 p-4 text-error">
      {{ errorMessage }}
    </div>
    <dl
        v-else-if="info"
        class="grid grid-cols-1 gap-3 rounded-theme border border-border-light dark:border-border-dark p-4 sm:grid-cols-2"
    >
      <div>
        <dt class="text-xs uppercase tracking-wider opacity-60">
          Version
        </dt>
        <dd class="font-mono text-sm">
          {{ info.version }}
        </dd>
      </div>
      <div>
        <dt class="text-xs uppercase tracking-wider opacity-60">
          Guilds
        </dt>
        <dd class="text-sm">
          {{ info.guildCount }}
        </dd>
      </div>
    </dl>
  </div>
</template>
