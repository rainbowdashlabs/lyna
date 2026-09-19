/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {type KioskProduct, listReleaseTypes, listVersions, type ReleaseTypeEntry, type VersionEntry} from '~/api/kiosk'
import {formatDate} from '~/util/format'

const {t} = useI18n()

const props = defineProps<{ product: KioskProduct }>()
const emit = defineEmits<{ download: [releaseType: ReleaseTypeEntry, version: VersionEntry] }>()

/** How many versions a page of the list adds. */
const PAGE = 10
/** The most the server will list at once. */
const MOST = 100

const releaseTypes = ref<ReleaseTypeEntry[]>([])
const selected = ref<ReleaseTypeEntry | null>(null)
const versions = ref<VersionEntry[]>([])
const limit = ref(PAGE)
const loading = ref(true)
const failed = ref(false)

/** A full page back means there may be more; a short one means that was all of them. */
const mayHaveMore = computed(() => versions.value.length === limit.value && limit.value < MOST)

async function show(releaseType: ReleaseTypeEntry, count = PAGE) {
  selected.value = releaseType
  limit.value = count
  loading.value = true
  failed.value = false
  try {
    versions.value = await listVersions(props.product.id, releaseType.id, count)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    releaseTypes.value = await listReleaseTypes(props.product.id)
    if (releaseTypes.value.length > 0) await show(releaseTypes.value[0]!)
    else loading.value = false
  } catch {
    failed.value = true
    loading.value = false
  }
})
</script>

<template>
  <aside :aria-label="t('ui.productVersions.versions')" class="space-y-3">
    <SectionLabel>{{ t('ui.productVersions.versions') }}</SectionLabel>

    <div v-if="releaseTypes.length > 1" class="flex flex-wrap gap-2">
      <SelectionToggleButton
          v-for="releaseType in releaseTypes"
          :key="releaseType.id"
          :selected="selected?.id === releaseType.id"
          @toggle="show(releaseType)"
      >
        {{ t(`ui.productVersions.releaseType.${releaseType.id}`) }}
      </SelectionToggleButton>
    </div>

    <MutedText v-if="failed" tag="p">{{ t('ui.productVersions.couldNotLoad') }}</MutedText>
    <MutedText v-else-if="!loading && versions.length === 0" tag="p">{{ t('ui.productVersions.noneYet') }}</MutedText>

    <div v-else>
      <GutterRow v-for="(version, index) in versions" :key="version.version" :marker="index + 1">
        <div class="flex items-baseline justify-between gap-3">
          <span class="font-data truncate text-sm">{{ version.version }}</span>
          <span class="font-data text-xs whitespace-nowrap text-(--text-muted)">{{ formatDate(version.publishedAt) }}</span>
        </div>
        <template #trailing>
          <DownloadButton v-if="selected?.downloadable" @click="emit('download', selected, version)"/>
        </template>
      </GutterRow>
      <Spinner v-if="loading" size="sm"/>
      <LinkButton v-else-if="mayHaveMore" @click="selected && show(selected, limit + PAGE)">
        {{ t('ui.productVersions.showMore') }}
      </LinkButton>
    </div>

    <EmptyHint v-if="selected && !selected.downloadable && versions.length > 0">
      {{ t('ui.productVersions.licenseToDownload') }}
    </EmptyHint>
  </aside>
</template>
