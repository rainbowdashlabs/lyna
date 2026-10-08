/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {deleteDownloadType, type DownloadType, editDownloadType} from '~/api/adminDownloads'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * A download type, renamed and redescribed in place. Deleting one still offered by a product is
 * refused, and the refusal names the products.
 */
const props = defineProps<{ guildId: string, type: DownloadType }>()
const emit = defineEmits<{ changed: [] }>()

const {t} = useI18n()
const name = ref(props.type.name)
const description = ref(props.type.description)
const {busy, done, error, run} = useAdminAction(() => t('ui.downloadTypeRow.couldNotSave'))

async function save() {
  await run(() => editDownloadType(props.guildId, props.type.id, {
    name: name.value, description: description.value, releaseType: props.type.releaseType,
  }))
  if (!error.value) emit('changed')
}

async function remove() {
  await run(() => deleteDownloadType(props.guildId, props.type.id))
  if (!error.value) emit('changed')
}
</script>

<template>
  <li :aria-label="type.name" class="space-y-2 p-3">
    <div class="grid items-end gap-3 sm:grid-cols-[1fr_2fr_auto_auto]">
      <LabelledField :label="t('ui.downloadTypeCreate.name')"><TextInput v-model="name"/></LabelledField>
      <LabelledField :label="t('ui.downloadTypeCreate.description')"><TextInput v-model="description"/></LabelledField>
      <span class="font-data border border-current px-2 py-1 text-xs">{{ type.releaseType }}</span>
      <div class="flex gap-2">
        <SecondaryButton :disabled="busy" compact @click="save">{{ t('ui.downloadTypeRow.save') }}</SecondaryButton>
        <ErrorButton :disabled="busy" compact @click="remove">{{ t('ui.downloadTypeRow.delete') }}</ErrorButton>
      </div>
    </div>
    <MutedText v-if="type.usedBy.length" size="xs" tag="p">{{ t('ui.downloadTypeRow.usedBy', {products: type.usedBy.join(', ')}) }}</MutedText>
    <MutedText v-if="done" size="xs">{{ t('ui.downloadTypeRow.saved') }}</MutedText>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </li>
</template>
