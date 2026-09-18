/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import IconButton from './IconButton.vue'
import {useI18n} from 'vue-i18n'

const {t} = useI18n()

defineProps<{
  disabled?: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <IconButton
      :disabled="disabled"
      :icon="['fas', 'pen']"
      class="text-info-accent hover:bg-info/15 dark:text-info"
      :label="t('common.edit')"
      @click="$emit('click', $event)"
  />
</template>
