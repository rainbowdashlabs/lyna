/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
withDefaults(defineProps<{
    label?: string
}>(), {
    label: 'Continue with Discord',
})
</script>

<template>
  <a
      class="block w-full rounded-theme border border-border-light py-2 text-center font-medium hover:bg-primary/10 dark:border-border-dark"
      href="/api/auth/discord/start"
  >
    {{ label }}
  </a>
</template>
