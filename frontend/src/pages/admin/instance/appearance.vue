<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {getInstanceAppearance, updateInstanceAppearance, type InstanceAppearance} from '~/api/admin'

const {t} = useI18n()

definePageMeta({layout: 'admin'})

const data = ref<InstanceAppearance | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const saveMessage = ref<string | null>(null)
const saving = ref(false)

onMounted(async () => {
  try {
    data.value = await getInstanceAppearance()
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
    await updateInstanceAppearance(data.value)
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
    <PageHeader class="mb-4">
      {{ t('page.admin.instance.appearance.appearance') }}
    </PageHeader>
    <AsyncSection :error="errorMessage ?? undefined" :loading="loading">
      <InstanceAppearanceForm
          v-if="data"
          v-model="data"
          :save-message="saveMessage"
          :saving="saving"
          @save="save"
      />
    </AsyncSection>
  </div>
</template>
