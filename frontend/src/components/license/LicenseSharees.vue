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
      <MutedText class="font-data">{{ license.shareesUsed }} / {{ license.shareesCap }}</MutedText>
    </CardHeader>
    <div v-if="sharees.length" class="mb-3 text-sm">
      <GutterRow
          v-for="(sharee, index) in sharees"
          :key="sharee.ref"
          :marker="index + 1"
          :pending="sharee.pending"
      >
        <span class="flex min-w-0 items-center gap-2">
          <KeyBadge class="truncate">{{ sharee.name }}</KeyBadge>
          <PrimaryBadge v-if="sharee.pending">{{ t('ui.licenseSharees.invited') }}</PrimaryBadge>
          <SecondaryBadge v-else>{{ t('ui.licenseSharees.granted') }}</SecondaryBadge>
        </span>
        <template #trailing>
          <SecondaryButton compact @click="$emit('revoke', sharee)">
            {{ sharee.pending ? t('ui.licenseSharees.withdraw') : t('common.revoke') }}
          </SecondaryButton>
        </template>
      </GutterRow>
    </div>
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
