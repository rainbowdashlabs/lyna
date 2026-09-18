/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'
</script>

<template>
  <BaseBadge bg-class="bg-secondary text-secondary-text border border-secondary">
    <slot/>
  </BaseBadge>
</template>
