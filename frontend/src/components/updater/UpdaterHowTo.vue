/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

const {t} = useI18n()

const exchange = `curl -X POST -H "Authorization: Bearer <upgrade token>" \\
  https://<this site>/api/v1/update/token`
const answer = `{"upgradeToken": "lyu_…", "downloadUrl": "…", "expiresAt": "…",
 "version": "1.2.0", "filename": "…", "sizeBytes": 123456}`
</script>

<template>
  <section class="space-y-2">
    <CardHeader>{{ t('ui.updaterHowTo.howItWorks') }}</CardHeader>
    <MutedText class="block" size="sm">{{ t('ui.updaterHowTo.trade') }}</MutedText>
    <CodeSnippet block>{{ exchange }}</CodeSnippet>
    <CodeSnippet block>{{ answer }}</CodeSnippet>
    <BulletList>
      <li>{{ t('ui.updaterHowTo.storeNewToken') }}</li>
      <li>{{ t('ui.updaterHowTo.linkLasts') }}</li>
      <li>{{ t('ui.updaterHowTo.chooseBuild') }}</li>
      <li>{{ t('ui.updaterHowTo.lostToken') }}</li>
    </BulletList>
  </section>
</template>
