<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {getInstanceAppearance, updateInstanceAppearance, type InstanceAppearance} from '~/api/admin'
import PrimaryButton from '~/components/button/PrimaryButton.vue'
import Spinner from '~/components/feedback/Spinner.vue'

definePageMeta({layout: 'admin'})

const data = ref<InstanceAppearance | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const saveMessage = ref<string | null>(null)
const saving = ref(false)

const enabledThemesInput = ref('')

onMounted(async () => {
  try {
    data.value = await getInstanceAppearance()
    enabledThemesInput.value = data.value.enabledThemes.join(', ')
  } catch (e) {
    const error = e as {response?: {status?: number}}
    errorMessage.value = error.response?.status === 404 ? 'Operator access required.' : 'Failed to load appearance settings.'
  } finally {
    loading.value = false
  }
})

async function save() {
  if (!data.value) return
  saving.value = true
  saveMessage.value = null
  try {
    const payload: InstanceAppearance = {
      ...data.value,
      enabledThemes: enabledThemesInput.value.split(',').map(s => s.trim()).filter(Boolean),
    }
    await updateInstanceAppearance(payload)
    data.value = payload
    saveMessage.value = 'Saved.'
  } catch (e) {
    const error = e as {response?: {data?: string}}
    saveMessage.value = typeof error.response?.data === 'string' ? error.response.data : 'Save failed.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <h1 class="mb-4 text-2xl font-bold">
      Appearance
    </h1>
    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <div v-else-if="errorMessage" class="rounded-theme border border-error/40 bg-error/10 p-4 text-error">
      {{ errorMessage }}
    </div>
    <form
        v-else-if="data"
        class="space-y-4 rounded-theme border border-border-light dark:border-border-dark p-4"
        @submit.prevent="save"
    >
      <label class="block text-sm">
        <span>Default theme</span>
        <input
            v-model="data.defaultTheme"
            class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
        />
      </label>
      <label class="block text-sm">
        <span>Default feel</span>
        <select
            v-model="data.defaultFeel"
            class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
        >
          <option value="ROUNDED">
            Rounded
          </option>
          <option value="CORNERS">
            Corners
          </option>
        </select>
      </label>
      <label class="block text-sm">
        <span>Enabled themes (comma-separated; empty = all)</span>
        <input
            v-model="enabledThemesInput"
            class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
        />
      </label>
      <div class="grid grid-cols-1 gap-2 sm:grid-cols-3 text-sm">
        <label class="flex items-center gap-2"><input v-model="data.lockFeel" type="checkbox" /> Lock feel</label>
        <label class="flex items-center gap-2"><input v-model="data.allowUserTheme" type="checkbox" /> Allow user theme</label>
        <label class="flex items-center gap-2"><input v-model="data.allowUserFeel" type="checkbox" /> Allow user feel</label>
      </div>
      <label class="block text-sm">
        <span>Custom theme colors (JSON)</span>
        <textarea
            v-model="data.customThemeColorsJson"
            rows="6"
            class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2 font-mono text-xs"
        />
      </label>
      <div v-if="saveMessage" class="text-sm" :class="saveMessage === 'Saved.' ? 'text-success' : 'text-error'">
        {{ saveMessage }}
      </div>
      <PrimaryButton :disabled="saving" @click="save">
        {{ saving ? 'Saving…' : 'Save' }}
      </PrimaryButton>
    </form>
  </div>
</template>
