/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * One option in a list the reader picks from: a full-width card carrying a title and, where there is
 * one, a line saying what it means.
 *
 * <p>The download wizard offers three such lists in turn - release type, version, download type -
 * and they are the same choice wearing different words, so they are the same component.
 */
defineProps<{
    title: string
    detail?: string
    disabled?: boolean
}>()

defineEmits<{
    click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      :disabled="disabled"
      class="w-full rounded-theme border border-border-light px-3 py-2 text-left transition-colors hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-border-dark"
      @click="$emit('click', $event)"
  >
    <div class="font-medium">{{ title }}</div>
    <MutedText v-if="detail" tag="div">{{ detail }}</MutedText>
  </button>
</template>
