/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

const {t} = useI18n()
</script>

<template>
  <section>
    <CardHeader>{{ t('ui.licenseUpdaterLink.directLinkUpdaters') }}</CardHeader>
    <MutedText class="block" size="sm">
      {{ t('ui.licenseUpdaterLink.aHeadlessUpdaterCanFetchFrom') }}
      <NuxtLink class="text-primary hover:underline" to="/account/updaters">{{ t('layout.account.updaters') }}</NuxtLink>
    </MutedText>
  </section>
</template>
