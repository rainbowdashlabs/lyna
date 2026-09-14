<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {getGuildSettings, updateGuildSettings, type GuildSettings} from '~/api/admin'
import PrimaryButton from '~/components/button/PrimaryButton.vue'
import Spinner from '~/components/feedback/Spinner.vue'

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))

const data = ref<GuildSettings | null>(null)
const loading = ref(true)
const saving = ref(false)
const message = ref<string | null>(null)
const isError = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await getGuildSettings(guildId.value)
  } finally {
    loading.value = false
  }
}

watch(() => route.params.guildId, (next) => {
  guildId.value = String(next)
  load()
})

onMounted(load)

async function save() {
  if (!data.value) return
  saving.value = true
  message.value = null
  isError.value = false
  try {
    await updateGuildSettings(guildId.value, data.value)
    message.value = 'Saved.'
  } catch (e) {
    const err = e as {response?: {data?: string}}
    isError.value = true
    message.value = typeof err.response?.data === 'string' ? err.response.data : 'Save failed.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader class="mb-4">
      Settings
    </PageHeader>
    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <form
        v-else-if="data"
        class="space-y-4 rounded-theme border border-border-light dark:border-border-dark p-4"
        @submit.prevent="save"
    >
      <label class="block text-sm">
        <span>License sharee cap</span>
        <input
            v-model.number="data.shares"
            type="number"
            min="0"
            class="mt-1 w-32 rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
        />
      </label>
      <label class="block text-sm">
        <span>Trial server time (minutes)</span>
        <input
            v-model.number="data.trialServerMinutes"
            type="number"
            min="0"
            class="mt-1 w-32 rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
        />
      </label>
      <label class="block text-sm">
        <span>Trial account time (minutes)</span>
        <input
            v-model.number="data.trialAccountMinutes"
            type="number"
            min="0"
            class="mt-1 w-32 rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
        />
      </label>
      <div v-if="message" class="text-sm" :class="isError ? 'text-error' : 'text-success'">
        {{ message }}
      </div>
      <PrimaryButton :disabled="saving" @click="save">
        {{ saving ? 'Saving…' : 'Save' }}
      </PrimaryButton>
    </form>
  </div>
</template>
