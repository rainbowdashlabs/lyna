/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'
</script>

<template>
  <!--
    A plain fact about a thing - what kind it is, where its name comes from - rather than a state
    somebody is in. Outlined, because the two fills are spoken for: blue says granted, pink says
    waiting on somebody, and a third filled badge would make both of them mean less.
  -->
  <BaseBadge>
    <slot/>
  </BaseBadge>
</template>
