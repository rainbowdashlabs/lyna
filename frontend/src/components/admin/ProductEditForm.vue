/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {type ProductSummary, updateGuildProduct} from '~/api/admin'

const {t} = useI18n()

const props = defineProps<{ guildId: string, product: ProductSummary }>()
const emit = defineEmits<{ saved: [] }>()

const name = ref(props.product.name)
const url = ref(props.product.url ?? '')
const roleId = ref(props.product.roleId)
const free = ref(props.product.free)
const trial = ref(props.product.trial)
const description = ref(props.product.description ?? '')
// An uploaded icon is served from this instance; that path is not an address to type back.
const iconUrl = ref(props.product.iconUrl?.startsWith('/api/') ? '' : props.product.iconUrl ?? '')

const busy = ref(false)
const errorMessage = ref<string | null>(null)
const saved = ref(false)

/**
 * Saves the whole product at once.
 *
 * <p>One request rather than one per field: a form that saves the name and then fails on the role has
 * already changed half of what somebody asked for.
 */
async function save() {
  busy.value = true
  errorMessage.value = null
  saved.value = false
  try {
    await updateGuildProduct(props.guildId, props.product.id, {
      name: name.value,
      url: url.value.trim() ? url.value.trim() : null,
      roleId: roleId.value,
      free: free.value,
      trial: trial.value,
      description: description.value.trim() ? description.value : null,
      iconUrl: iconUrl.value.trim() ? iconUrl.value.trim() : null,
    })
    saved.value = true
    emit('saved')
  } catch (e) {
    const error = e as { response?: { data?: string } }
    errorMessage.value = typeof error.response?.data === 'string'
        ? error.response.data
        : t('ui.productEditForm.couldNotSave')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form class="space-y-3" @submit.prevent="save">
    <LabelledField :label="t('ui.productEditForm.name')">
      <TextInput v-model="name" required/>
    </LabelledField>
    <LabelledField :label="t('ui.productEditForm.roleId')">
      <TextInput v-model="roleId"/>
    </LabelledField>
    <LabelledField :label="t('ui.productEditForm.projectUrl')">
      <TextInput v-model="url" type="url"/>
    </LabelledField>
    <LabelledField :label="t('ui.productEditForm.description')">
      <TextAreaInput v-model="description" :rows="6"/>
      <MutedText size="xs" tag="p">{{ t('ui.productEditForm.markdownShownOnThePage') }}</MutedText>
    </LabelledField>
    <LabelledField :label="t('ui.productEditForm.iconAddress')">
      <TextInput v-model="iconUrl" type="url"/>
      <MutedText size="xs" tag="p">{{ t('ui.productEditForm.usedWhenNothingIsUploaded') }}</MutedText>
    </LabelledField>
    <label class="flex items-center gap-2 text-sm">
      <CheckboxInput v-model="trial"/> {{ t('ui.productEditForm.trialAvailable') }}
    </label>
    <label class="flex items-center gap-2 text-sm">
      <CheckboxInput v-model="free"/> {{ t('ui.productEditForm.freeToEveryone') }}
    </label>
    <MutedText v-if="free !== product.free" class="block text-error" size="xs" tag="p">
      {{ t('ui.productEditForm.thisChangesWhoMayDownloadIt') }}
    </MutedText>
    <div class="flex items-center gap-3">
      <PrimaryButton :disabled="busy" type="submit">{{ t('ui.productEditForm.save') }}</PrimaryButton>
      <MutedText v-if="saved" size="sm">{{ t('ui.productEditForm.saved') }}</MutedText>
    </div>
    <MutedText v-if="errorMessage" class="block text-error" size="sm">{{ errorMessage }}</MutedText>
  </form>
</template>
