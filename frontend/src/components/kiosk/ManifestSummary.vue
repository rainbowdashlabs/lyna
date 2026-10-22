/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {VersionManifest} from '~/api/kiosk'
import {useVersionRange} from '~/composables/useVersionRange'

/**
 * What a build says it needs: the Minecraft versions and platforms it runs on, its Java version and
 * the plugins it depends on.
 */
const {t} = useI18n()
const range = useVersionRange()

defineProps<{ version: string, manifest: VersionManifest }>()
</script>

<template>
  <section class="space-y-2" data-testid="manifest-summary">
    <SectionLabel>{{ t('ui.manifestSummary.needs', {version}) }}</SectionLabel>
    <div class="flex flex-wrap items-center gap-2 text-sm">
      <span v-if="range(manifest.minecraftMin, manifest.minecraftMax)" class="font-data">
        {{ t('common.manifest.minecraft', {range: range(manifest.minecraftMin, manifest.minecraftMax)}) }}
      </span>
      <NeutralBadge v-for="platform in manifest.platforms" :key="platform">{{ platform }}</NeutralBadge>
      <MutedText v-if="manifest.java" class="font-data" size="xs">{{ t('common.manifest.java', {version: manifest.java}) }}</MutedText>
    </div>
    <ManifestDependencies v-if="manifest.dependencies.length" :dependencies="manifest.dependencies"/>
  </section>
</template>
