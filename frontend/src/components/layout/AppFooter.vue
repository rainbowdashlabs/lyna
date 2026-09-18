/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {getInstanceInfo, type InstanceInfo} from '~/api/kiosk'

const {t} = useI18n()

/**
 * What this instance is and where to find the people running it.
 *
 * <p>Asked for rather than built in, because the links are configuration and the version carries the
 * commit the running instance was built from - which is the thing somebody reporting a problem needs
 * to be able to read off the page.
 */
const info = ref<InstanceInfo | null>(null)

onMounted(async () => {
  try {
    info.value = await getInstanceInfo()
  } catch {
    // A footer is not worth an error message. Without it the links simply do not appear.
  }
})
</script>

<template>
  <footer class="mt-auto border-t border-border-light px-4 py-6 dark:border-border-dark">
    <div class="mx-auto flex max-w-6xl flex-col items-center gap-4 text-sm md:flex-row md:justify-between">
      <div class="flex flex-wrap items-center justify-center gap-4">
        <AppLink external href="https://github.com/rainbowdashlabs/lyna" :icon="['fab', 'github']">
          {{ t('ui.appFooter.source') }}
        </AppLink>
        <AppLink v-if="info?.discord" :href="info.discord" :icon="['fab', 'discord']" external>
          {{ t('ui.appFooter.discord') }}
        </AppLink>
        <AppLink v-if="info?.invite" :href="info.invite" :icon="['fab', 'discord']" external>
          {{ t('ui.appFooter.addTheBot') }}
        </AppLink>
        <AppLink v-if="info?.faq" :href="info.faq" external>{{ t('ui.appFooter.faq') }}</AppLink>
        <AppLink v-if="info?.terms" :href="info.terms" external>{{ t('ui.appFooter.terms') }}</AppLink>
      </div>

      <div class="flex flex-col items-center gap-1 text-(--text-muted) md:items-end">
        <span>{{ t('ui.appFooter.license') }}</span>
        <!-- The build, so somebody reporting a problem can say which one they were looking at. -->
        <span v-if="info?.version" class="font-data text-xs">{{ info.version }}</span>
      </div>
    </div>
  </footer>
</template>
