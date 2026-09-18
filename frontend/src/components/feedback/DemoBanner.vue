/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted} from 'vue'
import {useDemo} from '~/composables/useDemo'

const {t} = useI18n()

const {accounts, load} = useDemo()

onMounted(load)
</script>

<template>
  <div
      v-if="accounts"
      class="bg-info px-4 py-2 text-center text-sm text-info-text"
      role="status"
  >
    {{ t('ui.demoBanner.thisIsADemoAnyoneCan') }}
  </div>
</template>
