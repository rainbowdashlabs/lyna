/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {LicenseView} from '~/api/account'

const {t} = useI18n()

defineProps<{
    license: LicenseView
    licenseKey: string | null
    maskedKey: string
    keyShown: boolean
}>()

defineEmits<{
    toggle: []
    copy: []
}>()
</script>

<template>
  <header class="flex flex-wrap items-start gap-4">
    <ProductMonogram :name="license.productName"/>
    <div class="min-w-0 flex-1">
      <PageHeader>{{ license.productName }}</PageHeader>
      <div class="mt-1 flex flex-wrap items-center gap-1.5">
        <PrimaryBadge v-for="type in license.releaseTypes" :key="type">{{ type }}</PrimaryBadge>
        <MutedText>Issued to {{ license.userIdentifier }}</MutedText>
      </div>
    </div>
    <div v-if="licenseKey" class="flex items-center gap-2">
      <KeyBadge>{{ keyShown ? licenseKey : maskedKey }}</KeyBadge>
      <SecondaryButton compact @click="$emit('toggle')">{{ keyShown ? 'Hide' : 'Show' }}</SecondaryButton>
      <SecondaryButton compact @click="$emit('copy')">{{ t('ui.licenseDetailHeader.copy') }}</SecondaryButton>
    </div>
  </header>
</template>
