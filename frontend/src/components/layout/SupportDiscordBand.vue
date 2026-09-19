/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

const {t} = useI18n()

/**
 * Where to ask for help, set apart from the other links in the footer so nobody has to look for it.
 * The old download centre gave the support Discord the whole footer, and people found it there.
 */
defineProps<{ href: string }>()
</script>

<template>
  <section
      :aria-label="t('ui.supportDiscordBand.support')"
      class="border-b border-border-light bg-secondary/8 px-4 py-6 dark:border-border-dark"
  >
    <div class="mx-auto flex max-w-6xl flex-col items-center gap-4 text-center md:flex-row md:justify-between md:text-left">
      <div class="flex items-center gap-4">
        <font-awesome-icon :icon="['fab', 'discord']" class="text-4xl text-secondary-accent dark:text-secondary"/>
        <div>
          <p class="text-base font-semibold">{{ t('ui.supportDiscordBand.needHelp') }}</p>
          <p class="text-sm text-(--text-muted)">{{ t('ui.supportDiscordBand.whereToAsk') }}</p>
        </div>
      </div>
      <a
          :href="href"
          class="rounded-theme inline-flex items-center gap-2 border border-secondary px-4 py-2 text-sm font-semibold whitespace-nowrap transition-colors hover:bg-secondary/15"
          rel="noopener noreferrer"
          target="_blank"
      >
        <font-awesome-icon :icon="['fab', 'discord']"/>
        {{ t('ui.supportDiscordBand.join') }}
      </a>
    </div>
  </section>
</template>
