/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'

/**
 * Renders exactly one of the four states an async region can be in: loading, failed, loaded but
 * empty, or loaded with content. Replaces the hand-written
 * `<Spinner v-if="loading"/><Alert v-else-if="error"/><EmptyState v-else-if="!items.length"/>`
 * ladder that views used to repeat.
 *
 * Pairs with {@link useAsyncLoader}: bind `loading` and `error` straight from its return value and
 * `empty` to the emptiness of whatever the loader filled.
 *
 * Every branch is overridable through the `loading`, `error` and `empty` slots for views that need
 * a bespoke placeholder while keeping the branch logic here.
 */
withDefaults(defineProps<{
  loading: boolean
  /** Load failure message. Empty string means "no error", matching `useAsyncLoader`. */
  error?: string
  /** Whether the successful load produced nothing to show. */
  empty?: boolean
  /** Localized text for the default empty branch. Ignored when the `empty` slot is used. */
  emptyMessage?: string
  emptyCompact?: boolean
  spinnerSize?: 'sm' | 'md' | 'lg'
}>(), {
  error: '',
  empty: false,
  emptyMessage: '',
  emptyCompact: false,
  spinnerSize: 'lg',
})
</script>

<template>
  <div v-if="loading" class="flex justify-center py-8">
    <slot name="loading">
      <Spinner :size="spinnerSize"/>
    </slot>
  </div>
  <slot v-else-if="error" name="error">
    <Alert variant="error">{{ error }}</Alert>
  </slot>
  <slot v-else-if="empty" name="empty">
    <EmptyState :compact="emptyCompact" :message="emptyMessage"/>
  </slot>
  <slot v-else/>
</template>
