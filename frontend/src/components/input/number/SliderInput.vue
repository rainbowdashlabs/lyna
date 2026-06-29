/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'

const model = defineModel<number>({default: 0})

const props = withDefaults(defineProps<{
  min?: number
  max?: number
  step?: number
  disabled?: boolean
  showInput?: boolean
}>(), {
  min: 0,
  max: 100,
  step: 1,
  showInput: false,
})

const percentage = computed(() =>
    ((model.value - props.min) / (props.max - props.min)) * 100
)
</script>

<template>
  <div class="flex items-center gap-3">
    <input
        v-model.number="model"
        :disabled="disabled"
        :max="max"
        :min="min"
        :step="step"
        :style="{
        background: disabled
          ? undefined
          : `linear-gradient(to right, var(--color-primary) ${percentage}%, var(--color-bg-light-accent) ${percentage}%)`,
      }"
        class="w-full h-2 rounded-full appearance-none cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed bg-bg-light-accent dark:bg-bg-dark-accent"
        type="range"
    />
    <input
        v-if="showInput"
        v-model.number="model"
        :disabled="disabled"
        :max="max"
        :min="min"
        :step="step"
        class="w-16 shrink-0 px-2 py-1 text-sm text-center rounded-theme border border-bg-light-accent bg-bg-light text-(--text) outline-none focus:border-primary focus:ring-1 focus:ring-primary disabled:opacity-50 disabled:cursor-not-allowed dark:border-bg-dark-accent dark:bg-bg-dark scheme-light dark:scheme-dark"
        type="number"
    />
  </div>
</template>

<style scoped>
input[type="range"]::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 1rem;
  height: 1rem;
  border-radius: 9999px;
  background: var(--color-primary);
  cursor: pointer;
  border: 2px solid white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

input[type="range"]::-moz-range-thumb {
  width: 1rem;
  height: 1rem;
  border-radius: 9999px;
  background: var(--color-primary);
  cursor: pointer;
  border: 2px solid white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

input[type="range"]:disabled::-webkit-slider-thumb {
  cursor: not-allowed;
}

input[type="range"]:disabled::-moz-range-thumb {
  cursor: not-allowed;
}
</style>
