/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {listReleaseNotes, type ReleaseNote} from '~/api/kiosk'
import {formatDate} from '~/util/format'

/**
 * The product's releases on GitHub with their notes, newest first. Loaded when the tab is opened,
 * not with the page: most visitors come for the download, not for the history.
 */
const {t} = useI18n()

const props = defineProps<{ productId: number }>()

const releases = ref<ReleaseNote[]>([])
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    releases.value = await listReleaseNotes(props.productId)
  } catch {
    errorMessage.value = t('ui.productReleaseNotes.couldNotLoad')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <AsyncSection :empty="releases.length === 0" :empty-message="t('ui.productReleaseNotes.noneYet')" :error="errorMessage" :loading="loading">
    <div class="space-y-4" data-testid="release-notes">
      <SectionCard v-for="release in releases" :key="release.url">
        <header class="flex flex-wrap items-center gap-2">
          <SectionHeader class="break-words">{{ release.name ?? release.tag }}</SectionHeader>
          <MutedText v-if="release.name" class="font-data" size="xs">{{ release.tag }}</MutedText>
          <NeutralBadge v-if="release.prerelease">{{ t('ui.productReleaseNotes.prerelease') }}</NeutralBadge>
          <MutedText class="font-data ml-auto" size="xs">{{ formatDate(release.publishedAt) }}</MutedText>
        </header>
        <MarkdownBody v-if="release.body" :markdown="release.body" class="mt-3"/>
        <MutedText v-else class="mt-3" size="sm" tag="p">{{ t('ui.productReleaseNotes.noNotes') }}</MutedText>
        <a :href="release.url" class="mt-3 inline-block text-xs text-(--text-muted) hover:text-(--text)" rel="noopener noreferrer" target="_blank">
          <font-awesome-icon :icon="['fab', 'github']"/>
          <span class="ml-1">{{ t('ui.productReleaseNotes.onGithub') }}</span>
        </a>
      </SectionCard>
    </div>
  </AsyncSection>
</template>
