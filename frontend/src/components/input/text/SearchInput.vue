/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {nextTick, onMounted, ref} from 'vue'

const model = defineModel<string>()

const props = withDefaults(defineProps<{
  placeholder?: string
  disabled?: boolean
  autofocus?: boolean
}>(), {
  autofocus: false,
})

const inputEl = ref<HTMLInputElement | null>(null)

function focus() {
  inputEl.value?.focus()
}

function clear() {
  model.value = ''
}

onMounted(() => {
  if (props.autofocus) nextTick(focus)
})

defineExpose({focus})
</script>

<template>
  <div class="relative w-full">
    <span class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-primary">
      <font-awesome-icon :icon="['fas', 'magnifying-glass']" class="h-4 w-4"/>
    </span>
    <input
        ref="inputEl"
        v-model="model"
        :disabled="disabled"
        :placeholder="placeholder"
        type="search"
        class="w-full pl-10 pr-9 py-2 rounded-theme border-2 border-primary bg-bg-light text-(--text)
               placeholder:text-(--text-muted)
               transition-colors duration-150 outline-none
               focus:ring-2 focus:ring-primary/30
               disabled:opacity-50 disabled:cursor-not-allowed
               dark:bg-bg-dark"
    />
    <button
        v-if="model"
        type="button"
        :aria-label="placeholder ?? 'clear'"
        class="absolute inset-y-0 right-0 flex items-center pr-3 text-(--text-muted) hover:text-primary transition-colors"
        @click="clear"
    >
      <font-awesome-icon :icon="['fas', 'xmark']" class="h-4 w-4"/>
    </button>
  </div>
</template>
