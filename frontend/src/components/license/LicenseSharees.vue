/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {LicenseView} from '~/api/account'

const props = defineProps<{
    license: LicenseView
    sharees: string[]
    errorMessage: string | null
}>()

defineEmits<{
    add: []
    revoke: [discordId: string]
}>()

/** A cap of zero is a guild that set none, which is not the same as one that is full. */
const capReached = computed(() =>
    props.license.shareesCap > 0 && props.license.shareesUsed >= props.license.shareesCap)
</script>

<template>
  <section>
    <CardHeader class="flex items-center justify-between">
      <span>Sharees</span>
      <MutedText>{{ license.shareesUsed }} / {{ license.shareesCap }} used</MutedText>
    </CardHeader>
    <ul v-if="sharees.length" class="mb-3 space-y-2 text-sm">
      <li v-for="discordId in sharees" :key="discordId" class="flex items-center justify-between">
        <KeyBadge>{{ discordId }}</KeyBadge>
        <SecondaryButton compact @click="$emit('revoke', discordId)">Revoke</SecondaryButton>
      </li>
    </ul>
    <EmptyHint v-else class="mb-3">You have not shared this license with anyone.</EmptyHint>
    <PrimaryButton :disabled="capReached" compact @click="$emit('add')">Add sharee</PrimaryButton>
    <MutedText v-if="capReached" class="mt-2 block" size="sm">
      Cap reached. Revoke a sharee first, or ask the guild admin to raise the limit.
    </MutedText>
    <MutedText v-if="errorMessage" class="mt-2 block text-error" size="sm">{{ errorMessage }}</MutedText>
  </section>
</template>
