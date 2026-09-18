/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

const {t} = useI18n()
defineProps<{
    licenseKey: string
}>()
</script>

<template>
  <section>
    <CardHeader>{{ t('ui.licenseUpdaterLink.directLinkUpdaters') }}</CardHeader>
    <CodeSnippet block>/api/v1/download/direct/{{ licenseKey }}</CodeSnippet>
    <MutedText class="mt-2 block" size="sm">
      {{ t('ui.licenseUpdaterLink.aHeadlessUpdaterCanFetchFrom') }}
    </MutedText>
  </section>
</template>
