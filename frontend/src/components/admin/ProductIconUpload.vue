/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {deleteProductIcon, uploadProductIcon} from '~/api/admin'

const {t} = useI18n()

const props = defineProps<{
  guildId: string
  productId: number
  productName: string
  iconUrl: string | null
}>()

const emit = defineEmits<{ changed: [] }>()

const busy = ref(false)
const errorMessage = ref<string | null>(null)

/** Asked for again after every change, so the browser does not keep showing the previous one. */
const cacheBuster = ref(0)

async function pick(file: File) {
  busy.value = true
  errorMessage.value = null
  try {
    await uploadProductIcon(props.guildId, props.productId, file)
    cacheBuster.value += 1
    emit('changed')
  } catch (e) {
    const error = e as { response?: { data?: string } }
    errorMessage.value = typeof error.response?.data === 'string'
        ? error.response.data
        : t('ui.productIconUpload.couldNotUpload')
  } finally {
    busy.value = false
  }
}

async function remove() {
  busy.value = true
  errorMessage.value = null
  try {
    await deleteProductIcon(props.guildId, props.productId)
    cacheBuster.value += 1
    emit('changed')
  } catch {
    errorMessage.value = t('ui.productIconUpload.couldNotRemove')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="flex items-start gap-3">
    <ProductIcon
        :key="cacheBuster"
        :icon-url="iconUrl ? `${iconUrl}${iconUrl.includes('?') ? '&' : '?'}v=${cacheBuster}` : null"
        :name="productName"
        size="sm"
    />
    <div class="min-w-0 flex-1 space-y-2">
      <FileInput accept="image/png,image/jpeg,image/webp" :disabled="busy" @picked="pick"/>
      <MutedText size="xs" tag="p">{{ t('ui.productIconUpload.pngJpegOrWebp') }}</MutedText>
      <SecondaryButton v-if="iconUrl" compact :disabled="busy" @click="remove">
        {{ t('ui.productIconUpload.remove') }}
      </SecondaryButton>
      <MutedText v-if="errorMessage" class="mt-1 block text-error" size="sm">{{ errorMessage }}</MutedText>
    </div>
  </div>
</template>
