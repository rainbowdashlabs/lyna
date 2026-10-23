/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {getInstanceAppearance, updateInstanceAppearance, type InstanceAppearance} from '~/api/admin'
import {usePageTitle} from '~/composables/usePageTitle'

const {t} = useI18n()
usePageTitle(t('layout.admin.appearance'))

definePageMeta({layout: 'admin'})

const data = ref<InstanceAppearance | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const saveMessage = ref<string | null>(null)
const saving = ref(false)
const saveOk = ref(false)

onMounted(async () => {
  try {
    data.value = await getInstanceAppearance()
  } catch (e) {
    const error = e as {response?: {status?: number}}
    errorMessage.value = error.response?.status === 404
        ? t('page.admin.instance.appearance.operatorAccessRequired')
        : t('page.admin.instance.appearance.couldNotLoad')
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
    saveOk.value = true
    saveMessage.value = t('page.admin.instance.appearance.saved')
  } catch (e) {
    const error = e as {response?: {data?: string}}
    saveOk.value = false
    saveMessage.value = typeof error.response?.data === 'string' && error.response.data
        ? error.response.data
        : t('page.admin.instance.appearance.saveFailed')
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
          :save-ok="saveOk"
          :saving="saving"
          @save="save"
      />
    </AsyncSection>
  </div>
</template>
