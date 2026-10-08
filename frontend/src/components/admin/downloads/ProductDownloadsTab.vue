/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {type DownloadType, listDownloadTypes, listProductDownloads, type ProductDownload} from '~/api/adminDownloads'

/**
 * A product's downloads: what {@code /downloads download} manages.
 */
const props = defineProps<{ guildId: string, productId: number }>()

const {t} = useI18n()
const downloads = ref<ProductDownload[]>([])
const types = ref<DownloadType[]>([])
const loading = ref(true)
const error = ref('')

const unoffered = computed(() => types.value.filter(type => !downloads.value.some(download => download.typeId === type.id)))

async function load() {
  try {
    [downloads.value, types.value] = await Promise.all([
      listProductDownloads(props.guildId, props.productId),
      listDownloadTypes(props.guildId),
    ])
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <AsyncSection :empty="downloads.length === 0" :empty-message="t('ui.productDownloadsTab.none')" :error="error" :loading="loading">
      <ul class="divide-y divide-border-light border border-border-light dark:divide-border-dark dark:border-border-dark">
        <ProductDownloadRow v-for="download in downloads" :key="download.typeId" :download="download" :guild-id="guildId" :product-id="productId" @changed="load"/>
      </ul>
    </AsyncSection>
    <ProductDownloadCreate :guild-id="guildId" :product-id="productId" :types="unoffered" @created="load"/>
  </div>
</template>
