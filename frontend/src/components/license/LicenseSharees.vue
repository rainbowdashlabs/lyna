/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import type {LicenseView, ShareeView} from '~/api/account'

const {t} = useI18n()

const props = defineProps<{
    license: LicenseView
    sharees: ShareeView[]
    errorMessage: string | null
}>()

defineEmits<{
    add: []
    revoke: [sharee: ShareeView]
}>()

/** A cap of zero is a guild that set none, which is not the same as one that is full. */
const capReached = computed(() =>
    props.license.shareesCap > 0 && props.license.shareesUsed >= props.license.shareesCap)
</script>

<template>
  <section>
    <CardHeader class="flex items-center justify-between">
      <span>{{ t('ui.licenseSharees.sharees') }}</span>
      <MutedText>{{ license.shareesUsed }} / {{ license.shareesCap }} used</MutedText>
    </CardHeader>
    <ul v-if="sharees.length" class="mb-3 space-y-2 text-sm">
      <li v-for="sharee in sharees" :key="sharee.ref" class="flex items-center justify-between gap-2">
        <span class="flex min-w-0 items-center gap-2">
          <KeyBadge class="truncate">{{ sharee.name }}</KeyBadge>
          <MutedText v-if="sharee.pending" size="sm">{{ t('ui.licenseSharees.invited') }}</MutedText>
        </span>
        <SecondaryButton compact @click="$emit('revoke', sharee)">
          {{ sharee.pending ? 'Withdraw' : 'Revoke' }}
        </SecondaryButton>
      </li>
    </ul>
    <EmptyHint v-else class="mb-3">{{ t('ui.licenseSharees.youHaveNotSharedThisLicense') }}</EmptyHint>
    <PrimaryButton :disabled="capReached" compact @click="$emit('add')">{{ t('ui.licenseSharees.addSharee') }}</PrimaryButton>
    <MutedText v-if="capReached" class="mt-2 block" size="sm">
      {{ t('ui.licenseSharees.capReachedRevokeAShareeFirst') }}
    </MutedText>
    <MutedText v-else class="mt-2 block" size="sm">
      {{ t('ui.licenseSharees.shareByUsernameOrByEmail') }}
    </MutedText>
    <MutedText v-if="errorMessage" class="mt-2 block text-error" size="sm">{{ errorMessage }}</MutedText>
  </section>
</template>
