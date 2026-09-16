/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Standard table-header cell shared across compact admin / station tables. Wraps the
 * repeated `font-medium pr-3 py-1` class triple so the styling stays consistent and the
 * lint repeated-pattern threshold stays clear.
 */
</script>

<template>
  <th class="font-data border-b border-(--border) py-1.5 pr-3 text-[10px] tracking-[0.12em] text-(--text-muted) uppercase">
    <slot/>
  </th>
</template>
