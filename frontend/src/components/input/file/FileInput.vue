/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {BORDERED_INPUT_CLASSES} from '../inputClasses'

/**
 * A file to upload.
 *
 * <p>It carries no model: a file input cannot be given a value, only read from, so it hands the
 * chosen file to whoever asked and clears itself. Clearing matters - without it, choosing the same
 * file twice in a row raises no event and the second attempt looks like nothing happened.
 */
const props = defineProps<{
  /** What the picker offers, as an accept list. */
  accept?: string
  disabled?: boolean
}>()

const emit = defineEmits<{ picked: [file: File] }>()

const element = ref<HTMLInputElement | null>(null)

function choose(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) emit('picked', file)
  if (element.value) element.value.value = ''
}

defineExpose({accept: props.accept})
</script>

<template>
  <input
      ref="element"
      :accept="accept"
      :class="`${BORDERED_INPUT_CLASSES} w-full text-sm file:mr-3 file:rounded-theme file:border-0 file:bg-(--bg-accent) file:px-3 file:py-1 file:text-(--text)`"
      :disabled="disabled"
      type="file"
      @change="choose"
  />
</template>
