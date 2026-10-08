/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {type DownloadType, listDownloadTypes} from '~/api/adminDownloads'

/**
 * The kinds of build this guild offers - what {@code /downloads type} manages. A product's downloads
 * each name one of these.
 */
definePageMeta({layout: 'admin'})

const {t} = useI18n()
const guildId = String(useRoute().params.guildId)

const types = ref<DownloadType[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  try {
    types.value = await listDownloadTypes(guildId)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-6">
    <PageHeader>{{ t('page.admin.g.guildId.downloadTypes.title') }}</PageHeader>
    <MutedText size="sm" tag="p">{{ t('page.admin.g.guildId.downloadTypes.explain') }}</MutedText>
    <DownloadTypeCreate :guild-id="guildId" @created="load"/>
    <AsyncSection :empty="types.length === 0" :empty-message="t('page.admin.g.guildId.downloadTypes.none')" :error="error" :loading="loading">
      <ul class="divide-y divide-border-light border border-border-light dark:divide-border-dark dark:border-border-dark">
        <DownloadTypeRow v-for="type in types" :key="type.id" :guild-id="guildId" :type="type" @changed="load"/>
      </ul>
    </AsyncSection>
  </div>
</template>
