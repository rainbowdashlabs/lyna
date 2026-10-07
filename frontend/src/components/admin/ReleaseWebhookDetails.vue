/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {ReleaseWebhook} from '~/api/admin'

/**
 * What to enter in the repository's webhook settings on GitHub.
 */
defineProps<{
  webhook: ReleaseWebhook
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <MutedText size="sm" tag="p">{{ t('ui.releaseWebhookDetails.howTo') }}</MutedText>
    <LabelledField :label="t('ui.releaseWebhookDetails.payloadUrl')">
      <CodeSnippet block class="break-all select-all">{{ webhook.url }}</CodeSnippet>
    </LabelledField>
    <LabelledField :label="t('ui.releaseWebhookDetails.secret')">
      <CodeSnippet block class="break-all select-all">{{ webhook.secret }}</CodeSnippet>
    </LabelledField>
  </div>
</template>
