/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {deleteProductDownload, type DownloadEdit, editProductDownload, type ProductDownload} from '~/api/adminDownloads'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * One of a product's downloads: its type, its coordinates, and what Nexus holds for them.
 */
const props = defineProps<{ guildId: string, productId: number, download: ProductDownload }>()
const emit = defineEmits<{ changed: [] }>()

const {t} = useI18n()
const edit = ref<DownloadEdit>({
  typeId: props.download.typeId,
  repository: props.download.repository,
  groupId: props.download.groupId,
  artifactId: props.download.artifactId,
  classifier: props.download.classifier,
})
const {busy, error, run} = useAdminAction(() => t('ui.productDownloadRow.couldNotSave'))

async function save() {
  if (await run(() => editProductDownload(props.guildId, props.productId, props.download.typeId, edit.value))) emit('changed')
}

async function remove() {
  await run(() => deleteProductDownload(props.guildId, props.productId, props.download.typeId))
  if (!error.value) emit('changed')
}
</script>

<template>
  <li :aria-label="download.typeName" class="space-y-2 p-3">
    <div class="flex flex-wrap items-center gap-2">
      <span class="font-medium">{{ download.typeName }}</span>
      <span class="font-data border border-current px-1 text-[10px]">{{ download.releaseType }}</span>
      <MutedText class="font-data ml-auto" size="xs">
        {{ download.latestVersion ? t('ui.productDownloadRow.latest', {version: download.latestVersion}) : t('ui.productDownloadRow.nothingInNexus') }}
      </MutedText>
    </div>
    <DownloadCoordinates v-model="edit"/>
    <div class="flex gap-2">
      <SecondaryButton :disabled="busy" compact @click="save">{{ t('ui.productDownloadRow.save') }}</SecondaryButton>
      <ErrorButton :disabled="busy" compact @click="remove">{{ t('ui.productDownloadRow.remove') }}</ErrorButton>
    </div>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </li>
</template>
