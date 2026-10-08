/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {createProductDownload, type DownloadEdit, type DownloadType} from '~/api/adminDownloads'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * Adding a download to a product: a type it does not offer yet, and where its builds are.
 */
const props = defineProps<{ guildId: string, productId: number, types: DownloadType[] }>()
const emit = defineEmits<{ created: [] }>()

const {t} = useI18n()
const empty = (): DownloadEdit => ({typeId: null, repository: '', groupId: '', artifactId: '', classifier: null})
const edit = ref<DownloadEdit>(empty())
const {busy, error, run} = useAdminAction(() => t('ui.productDownloadCreate.couldNotCreate'))

async function create() {
  if (!await run(() => createProductDownload(props.guildId, props.productId, edit.value))) return
  edit.value = empty()
  emit('created')
}
</script>

<template>
  <section class="space-y-3 border border-border-light p-4 dark:border-border-dark">
    <CardHeader>{{ t('ui.productDownloadCreate.title') }}</CardHeader>
    <EmptyHint v-if="!types.length">{{ t('ui.productDownloadCreate.noTypes') }}</EmptyHint>
    <template v-else>
      <LabelledField :label="t('ui.productDownloadCreate.type')">
        <SelectInput v-model="edit.typeId">
          <option :value="null" disabled>{{ t('ui.productDownloadCreate.choose') }}</option>
          <option v-for="type in types" :key="type.id" :value="type.id">{{ type.name }} ({{ type.releaseType }})</option>
        </SelectInput>
      </LabelledField>
      <DownloadCoordinates v-model="edit"/>
      <PrimaryButton :disabled="busy || edit.typeId === null" @click="create">{{ t('ui.productDownloadCreate.add') }}</PrimaryButton>
    </template>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </section>
</template>
