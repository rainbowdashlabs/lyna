<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {getGuildSettings, updateGuildSettings, type GuildSettings} from '~/api/admin'
import PrimaryButton from '~/components/button/PrimaryButton.vue'
import Spinner from '~/components/feedback/Spinner.vue'

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))

const data = ref<GuildSettings | null>(null)

/**
 * The role id as the field holds it. Kept apart from the payload so that clearing the box means
 * "no role" rather than a role whose id is the empty string.
 */
const adminRoleId = computed({
  get: () => data.value?.adminRoleId ?? '',
  set: (value: string) => {
    if (data.value) data.value.adminRoleId = value.trim() ? value.trim() : null
  },
})
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
      <LabelledField label="License sharee cap">
        <NumberInput v-model="data.shares" class="w-32" min="0"/>
      </LabelledField>
      <LabelledField label="Trial server time (minutes)">
        <NumberInput v-model="data.trialServerMinutes" class="w-32" min="0"/>
      </LabelledField>
      <LabelledField label="Trial account time (minutes)">
        <NumberInput v-model="data.trialAccountMinutes" class="w-32" min="0"/>
      </LabelledField>
      <LabelledField
          help="Members of this role administer this guild here. Leave empty so only Manage Server does."
          label="Admin role id (optional)"
      >
        <TextInput v-model="adminRoleId" inputmode="numeric" placeholder="e.g. 1065674230362017813"/>
      </LabelledField>
      <div v-if="message" class="text-sm" :class="isError ? 'text-error' : 'text-success'">
        {{ message }}
      </div>
      <PrimaryButton :disabled="saving" @click="save">
        {{ saving ? 'Saving…' : 'Save' }}
      </PrimaryButton>
    </form>
  </div>
</template>
