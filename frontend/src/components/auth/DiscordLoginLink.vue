/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {discordStartUrl, rememberSignInMethod} from '~/util/signIn'

withDefaults(defineProps<{
    label?: string
}>(), {
    label: 'Continue with Discord',
})
</script>

<template>
  <a
      class="block w-full rounded-theme border border-border-light py-2 text-center font-medium hover:bg-primary/10 dark:border-border-dark"
      :href="discordStartUrl()"
      @click="rememberSignInMethod('discord')"
  >
    {{ label }}
  </a>
</template>
