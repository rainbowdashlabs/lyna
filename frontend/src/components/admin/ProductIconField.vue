/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {setProductIcon} from '~/api/admin'

const {t} = useI18n()

const props = defineProps<{
    guildId: string
    productId: number
    productName: string
    iconUrl: string | null
}>()

const emit = defineEmits<{
    saved: [iconUrl: string]
}>()

const value = ref(props.iconUrl ?? '')
const busy = ref(false)
const errorMessage = ref<string | null>(null)

/**
 * Stores the address after the backend has checked that something image-shaped answers there.
 * A rejection is shown here rather than swallowed, because the alternative is a tile that has
 * quietly lost its icon.
 */
async function save() {
  busy.value = true
  errorMessage.value = null
  try {
    await setProductIcon(props.guildId, props.productId, value.value)
    emit('saved', value.value)
  } catch (e) {
    const error = e as {response?: {data?: string}}
    errorMessage.value = typeof error.response?.data === 'string'
        ? error.response.data
        : 'Could not save the icon.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="flex items-start gap-3">
    <ProductIcon :icon-url="value || null" :name="productName" size="sm"/>
    <div class="min-w-0 flex-1">
      <TextInput v-model="value" :placeholder="t('ui.productIconField.httpsIconPng')" type="url"/>
      <MutedText v-if="errorMessage" class="mt-1 block text-error" size="sm">{{ errorMessage }}</MutedText>
    </div>
    <SecondaryButton :disabled="busy" compact @click="save">{{ busy ? 'Saving…' : 'Save icon' }}</SecondaryButton>
  </div>
</template>
