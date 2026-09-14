/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
    licenseKey: string
}>()
</script>

<template>
  <section>
    <CardHeader>Direct-link updaters</CardHeader>
    <CodeSnippet block>/api/v1/download/direct/{{ licenseKey }}</CodeSnippet>
    <MutedText class="mt-2 block" size="sm">
      A headless updater can fetch from this address without signing in. Treat it as the key it carries.
    </MutedText>
  </section>
</template>
