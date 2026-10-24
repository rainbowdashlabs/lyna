/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import type {HeldKey} from '~/api/adminUpdateTokens'
import {formatDateTime} from '~/util/format'

/**
 * A dashboard key somebody holds for one of the guild's products, as an admin sees it: whose, how many
 * that account holds of the product, and how it is used. Revoking takes a second click, since it cuts
 * the server off at its next verification.
 */
const {t} = useI18n()

defineProps<{
    held: HeldKey
    /** How many dashboard keys the same account holds for the same product. */
    ofAccount: number
    marker: number
    busy: boolean
}>()

defineEmits<{
    revoke: []
}>()

const confirming = ref(false)
</script>

<template>
  <GutterRow :aria-label="`${held.account} ${held.name}`" :marker="marker" role="listitem">
    <div class="space-y-1">
      <div class="flex flex-wrap items-center gap-2">
        <KeyBadge>{{ held.account }}</KeyBadge>
        <span>{{ held.productName }} · {{ held.keyTypeName }}</span>
        <span class="font-medium">{{ held.name }}</span>
        <MutedText class="font-data" size="xs">{{ t('ui.heldKeyRow.ofAccount', {count: ofAccount}) }}</MutedText>
      </div>
      <MutedText class="font-data block" size="xs">
        {{ held.lastUsedAt ? t('ui.updaterTokenRow.lastUsed', {at: formatDateTime(held.lastUsedAt)}) : t('ui.updaterTokenRow.neverUsed') }} ·
        {{ t('ui.updaterTokenRow.expires', {at: formatDateTime(held.expiresAt)}) }}
      </MutedText>
    </div>
    <template #trailing>
      <SecondaryButton v-if="!confirming" :disabled="busy" compact @click="confirming = true">{{ t('common.revoke') }}</SecondaryButton>
      <SecondaryButton v-else :disabled="busy" compact @click="confirming = false; $emit('revoke')">
        {{ t('ui.updaterTokenRow.reallyRevoke') }}
      </SecondaryButton>
    </template>
  </GutterRow>
</template>
