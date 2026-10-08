/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Every page outside the account and admin areas: the page, and the site's footer under it. The footer
 * lives here rather than in the pages, so no page can leave it out.
 */
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <div class="flex flex-1 flex-col">
      <slot/>
    </div>
    <AppFooter/>
  </div>
</template>
