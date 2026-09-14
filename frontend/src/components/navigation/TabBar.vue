/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
interface Tab {
  key: string
  label: string
}

const modelValue = defineModel<string>({required: true})

defineProps<{
  tabs: Tab[]
}>()
</script>

<template>
  <!--
    The rule belongs to the outer element and the scrolling to the inner one, which is what keeps a
    horizontal scroller from growing a vertical scrollbar. `overflow-x: auto` makes the other axis
    compute to `auto` as well, so anything reaching past the bottom edge is enough for Chrome to show a
    second bar: the active tab's border, pulled down a pixel to sit on the rule, was exactly that. Moving
    that pull onto the scroller leaves no child overhanging it, and `overflow-y-hidden` says so outright.
  -->
  <div class="border-b border-bg-light-accent dark:border-bg-dark-accent">
    <div class="-mb-px flex gap-2 overflow-x-auto overflow-y-hidden">
      <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="modelValue === tab.key ? 'border-primary text-primary' : 'border-transparent text-(--text-muted) hover:text-(--text)'"
          class="shrink-0 whitespace-nowrap px-4 py-2 text-sm font-medium transition-colors border-b-2"
          @click="modelValue = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>
  </div>
</template>
