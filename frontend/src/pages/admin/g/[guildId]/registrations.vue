<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {lookupRegistration, type RegistrationInfo} from '~/api/admin'
import PrimaryButton from '~/components/button/PrimaryButton.vue'

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))
const discordIdInput = ref('')
const result = ref<RegistrationInfo | null>(null)
const errorMessage = ref<string | null>(null)
const busy = ref(false)

watch(() => route.params.guildId, (next) => {
  guildId.value = String(next)
  result.value = null
})

async function submit() {
  if (!discordIdInput.value.trim()) return
  busy.value = true
  errorMessage.value = null
  try {
    result.value = await lookupRegistration(guildId.value, discordIdInput.value.trim())
  } catch (e) {
    const error = e as {response?: {data?: string, status?: number}}
    if (error.response?.status === 404) errorMessage.value = 'No registration found.'
    else errorMessage.value = typeof error.response?.data === 'string' ? error.response.data : 'Lookup failed.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div>
    <h1 class="mb-4 text-2xl font-bold">
      Registrations
    </h1>
    <p class="mb-4 text-sm opacity-70">
      Look up a registration by Discord user id.
    </p>
    <form class="mb-4 flex gap-2" @submit.prevent="submit">
      <input
          v-model="discordIdInput"
          inputmode="numeric"
          placeholder="Discord user id"
          class="flex-1 rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
      />
      <PrimaryButton :disabled="busy" @click="submit">
        {{ busy ? 'Looking up…' : 'Look up' }}
      </PrimaryButton>
    </form>
    <div v-if="errorMessage" class="text-sm text-error">
      {{ errorMessage }}
    </div>
    <div
        v-if="result"
        class="rounded-theme border border-border-light dark:border-border-dark p-4 text-sm"
    >
      <div>
        <span class="opacity-70">Discord id:</span> {{ result.discordId }}
      </div>
      <div>
        <span class="opacity-70">Member name:</span> {{ result.memberName ?? '— not in this guild —' }}
      </div>
    </div>
  </div>
</template>
