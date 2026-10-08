/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {deleteGuildProduct, type ProductSummary} from '~/api/admin'

/**
 * Deleting a product, which takes its licenses, downloads and everything else with it. The name has
 * to be typed out first, as GitHub asks for a repository's.
 */
const props = defineProps<{ guildId: string, product: ProductSummary }>()
const emit = defineEmits<{ deleted: [] }>()

const {t} = useI18n()

const typed = ref('')
const busy = ref(false)
const errorMessage = ref<string | null>(null)

async function remove() {
  busy.value = true
  errorMessage.value = null
  try {
    await deleteGuildProduct(props.guildId, props.product.id, typed.value)
    emit('deleted')
  } catch (e) {
    const error = e as { response?: { data?: string } }
    errorMessage.value = typeof error.response?.data === 'string' && error.response.data
        ? error.response.data
        : t('ui.productDeleteZone.couldNotDelete')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="space-y-3 border border-error p-4">
    <SectionLabel>{{ t('ui.productDeleteZone.title') }}</SectionLabel>
    <MutedText size="sm" tag="p">{{ t('ui.productDeleteZone.explain', {name: product.name}) }}</MutedText>
    <LabelledField :label="t('ui.productDeleteZone.typeName')">
      <TextInput v-model="typed"/>
    </LabelledField>
    <ErrorButton :disabled="busy || typed !== product.name" @click="remove">{{ t('ui.productDeleteZone.delete') }}</ErrorButton>
    <MutedText v-if="errorMessage" class="block text-error" size="sm">{{ errorMessage }}</MutedText>
  </section>
</template>
