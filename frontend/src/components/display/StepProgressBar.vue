/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
const currentStep = defineModel<number>('currentStep', {required: true})

defineProps<{
  steps: { label: string; disabled?: boolean }[]
}>()

function selectStep(index: number, disabled?: boolean) {
  if (!disabled) currentStep.value = index
}
</script>

<template>
  <div class="flex items-center w-full gap-0">
    <template v-for="(step, index) in steps" :key="index">
      <!-- Chevron separator -->
      <font-awesome-icon
          v-if="index > 0"
          :icon="['fas', 'chevron-right']"
          class="shrink-0 mx-1 text-xs"
          :class="index <= currentStep ? 'text-primary' : 'text-(--text-muted) opacity-40'"
      />
      <!-- Step -->
      <div
          class="flex-1 flex items-center justify-center gap-2 py-2.5 px-3 rounded-lg text-sm font-medium cursor-pointer select-none transition-colors duration-200"
          :class="[
            index === currentStep
              ? 'bg-primary text-primary-text shadow-sm'
              : index < currentStep
                ? 'bg-primary/15 text-primary'
                : step.disabled
                  ? 'bg-(--bg-accent) text-(--text-muted) cursor-not-allowed opacity-50'
                  : 'bg-(--bg-accent) text-(--text) hover:bg-(--bg-accent)/80',
          ]"
          @click="selectStep(index, step.disabled)"
      >
        <span
            class="flex items-center justify-center w-6 h-6 rounded-full text-xs font-bold shrink-0"
            :class="index === currentStep ? 'bg-white/25' : index < currentStep ? 'bg-primary/20' : 'bg-(--border)'"
        >
          <font-awesome-icon v-if="index < currentStep" :icon="['fas', 'check']" class="text-xs"/>
          <template v-else>{{ index + 1 }}</template>
        </span>
        <span class="hidden sm:inline truncate">{{ step.label }}</span>
      </div>
    </template>
  </div>
</template>
