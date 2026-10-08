/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {type ProductSummary, setProductDescription} from '~/api/admin'

/**
 * What the product's public page says, written as markdown beside how it will look.
 */
const props = defineProps<{ guildId: string, product: ProductSummary }>()
const emit = defineEmits<{ saved: [] }>()

const {t} = useI18n()

const markdown = ref(props.product.description ?? '')
const busy = ref(false)
const saved = ref(false)
const errorMessage = ref<string | null>(null)

async function save() {
  busy.value = true
  saved.value = false
  errorMessage.value = null
  try {
    await setProductDescription(props.guildId, props.product.id, markdown.value)
    saved.value = true
    emit('saved')
  } catch (e) {
    const error = e as { response?: { data?: string } }
    errorMessage.value = typeof error.response?.data === 'string' && error.response.data
        ? error.response.data
        : t('ui.productPageEditor.couldNotSave')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="space-y-3">
    <ProductReadmeSource :guild-id="guildId" :product="product"/>
    <div class="grid gap-4 lg:grid-cols-2">
      <LabelledField :label="t('ui.productPageEditor.markdown')">
        <TextAreaInput v-model="markdown" :rows="20" class="font-data"/>
      </LabelledField>
      <div class="space-y-1">
        <SectionLabel>{{ t('ui.productPageEditor.preview') }}</SectionLabel>
        <div class="border border-border-light p-4 dark:border-border-dark">
          <ProductDescription :markdown="markdown || null"/>
        </div>
      </div>
    </div>
    <div class="flex items-center gap-3">
      <PrimaryButton :disabled="busy" @click="save">{{ t('ui.productPageEditor.save') }}</PrimaryButton>
      <MutedText v-if="saved" size="sm">{{ t('ui.productPageEditor.saved') }}</MutedText>
    </div>
    <MutedText v-if="errorMessage" class="block text-error" size="sm">{{ errorMessage }}</MutedText>
  </div>
</template>
