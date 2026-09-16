/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted} from 'vue'
import {useDemo} from '~/composables/useDemo'

const {accounts, load} = useDemo()

onMounted(load)
</script>

<template>
  <div
      v-if="accounts"
      class="bg-info px-4 py-2 text-center text-sm text-info-text"
      role="status"
  >
    This is a demo. Anyone can sign in, everything here is made up, and it is all thrown away and
    laid out again on a timer.
  </div>
</template>
