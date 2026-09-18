/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {addSharee, licenseDetail, type LicenseDetail, removeSharee, type ShareeView} from '~/api/account'

const {t} = useI18n()

definePageMeta({layout: 'account'})

const route = useRoute()
const licenseId = Number(route.params.id)

const data = ref<LicenseDetail | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)

const keyShown = ref(false)
const addShown = ref(false)
const shareeInput = ref('')
const shareeError = ref<string | null>(null)
const revoking = ref<ShareeView | null>(null)

const isOwner = computed(() => data.value?.license.role === 'owner')
const maskedKey = computed(() => (data.value?.key ? '•'.repeat(data.value.key.length) : ''))

async function refresh() {
  data.value = await licenseDetail(licenseId)
}

onMounted(async () => {
  try {
    await refresh()
  } catch (e) {
    const error = e as {response?: {status?: number}}
    errorMessage.value = error.response?.status === 404
        ? 'This license does not exist, or is not yours.'
        : 'Failed to load the license.'
  } finally {
    loading.value = false
  }
})

async function doAddSharee() {
  shareeError.value = null
  try {
    await addSharee(licenseId, shareeInput.value)
    shareeInput.value = ''
    addShown.value = false
    await refresh()
  } catch (e) {
    const error = e as {response?: {status?: number, data?: string}}
    shareeError.value = typeof error.response?.data === 'string'
        ? error.response.data
        : 'Could not share the license.'
  }
}

async function doRevoke() {
  const sharee = revoking.value
  if (!sharee) return
  revoking.value = null
  await removeSharee(licenseId, sharee.ref)
  await refresh()
}

async function copyKey() {
  if (data.value?.key) await navigator.clipboard.writeText(data.value.key)
}
</script>

<template>
  <AsyncSection :error="errorMessage ?? undefined" :loading="loading">
    <div v-if="data" class="space-y-6">
      <LicenseDetailHeader
          :key-shown="keyShown"
          :license="data.license"
          :license-key="data.key"
          :masked-key="maskedKey"
          @copy="copyKey"
          @toggle="keyShown = !keyShown"
      />
      <LicenseUsage :rows="data.recentDownloads"/>
      <LicenseSharees
          v-if="isOwner"
          :error-message="shareeError"
          :license="data.license"
          :sharees="data.sharees"
          @add="addShown = true"
          @revoke="revoking = $event"
      />
      <LicenseUpdaterLink v-if="data.key" :license-key="data.key"/>
      <EmptyHint v-if="!isOwner">
        {{ t('page.account.licenses.id.theLicenseOwnerCanSeeWhen') }}
      </EmptyHint>

      <SingleFieldModal
          v-model:show="addShown"
          v-model:value="shareeInput"
          :confirm-label="t('page.account.licenses.id.share')"
          :placeholder="t('page.account.licenses.id.usernameOrEmailAddress')"
          :title="t('page.account.licenses.id.shareThisLicense')"
          @confirm="doAddSharee"
      />
      <ConfirmDeleteModal
          :message="revoking?.pending
              ? `Withdraw the invitation to ${revoking.name}? They will not be able to accept it.`
              : `Revoke ${revoking?.name}'s access to ${data.license.productName}? They will lose access immediately.`"
          :model-value="revoking !== null"
          @confirm="doRevoke"
          @update:model-value="revoking = $event ? revoking : null"
      />
    </div>
  </AsyncSection>
</template>
