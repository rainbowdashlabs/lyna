/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {ManifestDependency} from '~/api/kiosk'
import {useVersionRange} from '~/composables/useVersionRange'
import {linkSite} from '~/util/minecraft'

/**
 * The plugins a build needs or can use, each with the versions it works with and where it is found.
 * One that is also a product here leads to its page first.
 */
const {t} = useI18n()
const range = useVersionRange()

defineProps<{ dependencies: ManifestDependency[] }>()
</script>

<template>
  <ul class="space-y-2" data-testid="manifest-dependencies">
    <li v-for="dependency in dependencies" :key="dependency.name" class="text-sm">
      <div class="flex flex-wrap items-baseline gap-2">
        <NuxtLink v-if="dependency.productId != null" :to="`/products/${dependency.productId}`" class="font-medium hover:underline">
          {{ dependency.name }}
        </NuxtLink>
        <span v-else class="font-medium">{{ dependency.name }}</span>
        <MutedText size="xs">{{ dependency.required ? t('common.manifest.required') : t('common.manifest.optional') }}</MutedText>
        <MutedText v-if="range(dependency.min, dependency.max)" class="font-data" size="xs">{{ range(dependency.min, dependency.max) }}</MutedText>
      </div>
      <div v-if="dependency.links.length" class="flex flex-wrap gap-x-3 text-xs">
        <a
            v-for="link in dependency.links"
            :key="link.url"
            :href="link.url"
            class="text-(--text-muted) hover:text-(--text)"
            rel="noopener noreferrer nofollow"
            target="_blank"
        >{{ linkSite(link) }}</a>
      </div>
    </li>
  </ul>
</template>
