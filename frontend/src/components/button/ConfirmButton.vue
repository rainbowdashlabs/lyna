/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from './IconButton.vue'

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
      :icon="['fas', 'check']"
      class="border-transparent text-success hover:border-success hover:bg-success/15"
      :label="t('common.confirm')"
      @click="$emit('click', $event)"
  />
</template>
