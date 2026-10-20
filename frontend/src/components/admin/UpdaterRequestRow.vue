/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {UpdateTokenRequest} from '~/api/updateTokens'
import {formatDateTime} from '~/util/format'

const {t} = useI18n()

defineProps<{
    request: UpdateTokenRequest
    marker: number
    busy: boolean
}>()

defineEmits<{
    decide: [approve: boolean]
}>()
</script>

<template>
  <GutterRow :aria-label="request.account" :marker="marker" pending role="listitem">
    <div class="space-y-1">
      <div class="flex flex-wrap items-center gap-2">
        <KeyBadge>{{ request.account }}</KeyBadge>
        <span>{{ request.productName }}</span>
        <span class="font-data">+{{ request.amount }}</span>
      </div>
      <div>{{ request.reason }}</div>
      <MutedText class="font-data block" size="xs">{{ formatDateTime(request.createdAt) }}</MutedText>
    </div>
    <template #trailing>
      <div class="flex gap-1">
        <PrimaryButton :disabled="busy" compact @click="$emit('decide', true)">{{ t('ui.updaterRequestRow.approve') }}</PrimaryButton>
        <SecondaryButton :disabled="busy" compact @click="$emit('decide', false)">{{ t('ui.updaterRequestRow.decline') }}</SecondaryButton>
      </div>
    </template>
  </GutterRow>
</template>
