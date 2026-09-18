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
      :icon="['fas', 'download']"
      class="border-transparent text-secondary-accent hover:border-secondary hover:bg-secondary/15 dark:text-secondary dark:hover:bg-secondary/15"
      :label="t('common.download')"
      @click="$emit('click', $event)"
  />
</template>
