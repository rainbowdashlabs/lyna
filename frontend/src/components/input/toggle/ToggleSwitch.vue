/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
const model = defineModel<string>({required: true})

const props = defineProps<{
  optionA: string
  optionB: string
  labelA: string
  labelB: string
  disabled?: boolean
}>()

function toggle() {
  model.value = model.value === props.optionA ? props.optionB : props.optionA
}
</script>

<template>
  <div class="inline-flex items-center gap-1.5">
    <span class="text-xs font-medium transition-colors" :class="model === optionA ? 'text-primary' : 'text-(--text-muted)'">{{ labelA }}</span>
    <button
        type="button"
        role="switch"
        :aria-checked="model === optionB"
        :disabled="disabled"
        class="relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full bg-primary/20 border-2 border-transparent transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-1 disabled:opacity-50 disabled:cursor-not-allowed"
        @click="toggle"
    >
      <span
          :class="model === optionB ? 'translate-x-4' : 'translate-x-0'"
          class="pointer-events-none inline-block h-4 w-4 rounded-full bg-primary shadow ring-0 transition-transform duration-200"
      />
    </button>
    <span class="text-xs font-medium transition-colors" :class="model === optionB ? 'text-primary' : 'text-(--text-muted)'">{{ labelB }}</span>
  </div>
</template>
