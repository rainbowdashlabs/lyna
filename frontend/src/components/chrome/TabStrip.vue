/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
export interface Tab {
    to: string
    label: string
    /** Match the route exactly, for a tab whose path is a prefix of its siblings'. */
    exact?: boolean
}

defineProps<{
    tabs: Tab[]
    /** A control at the far end of the strip, shaped like a tab rather than sitting on one. */
    action?: string
}>()

defineEmits<{
    action: []
}>()
</script>

<template>
  <nav class="flex overflow-x-auto border-b border-border-light bg-bg-light-accent dark:border-border-dark dark:bg-bg-dark-accent">
    <NuxtLink
        v-for="tab in tabs"
        :key="tab.to"
        :to="tab.to"
        :exact-active-class="tab.exact ? 'tab-on' : ''"
        :active-class="tab.exact ? '' : 'tab-on'"
        class="font-data shrink-0 border-r border-border-light px-4 py-2.5 text-xs whitespace-nowrap text-(--text-muted) hover:bg-primary/10 hover:text-(--text) dark:border-border-dark"
    >
      {{ tab.label }}
    </NuxtLink>
    <div class="ml-auto flex shrink-0 items-center">
      <slot name="end"/>
      <button
          v-if="action"
          class="font-data cursor-pointer border-l border-border-light px-4 py-2.5 text-xs whitespace-nowrap text-(--text-muted) hover:bg-primary/10 hover:text-(--text) dark:border-border-dark"
          type="button"
          @click="$emit('action')"
      >
        {{ action }}
      </button>
    </div>
  </nav>
</template>

<style scoped>
/*
 * The active tab is the one thing on the strip that is filled, and the pink edge along its top is
 * the same mark used everywhere else for "this one". Bringing it up over the strip's own border is
 * what makes the tab read as the front of the panel below rather than a button on a bar.
 */
.tab-on {
  background: var(--bg);
  color: var(--text);
  box-shadow: inset 0 2px 0 var(--color-primary);
}
</style>
