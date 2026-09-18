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
      :icon="['fas', 'trash']"
      class="text-error hover:bg-error/15"
      :label="t('common.delete')"
      @click="$emit('click', $event)"
  />
</template>
