/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

const {t} = useI18n()
const page = defineModel<number>({required: true})

defineProps<{
    pageCount: number
    total: number
}>()
</script>

<template>
  <div v-if="pageCount > 1" class="mt-3 flex items-center justify-between">
    <MutedText size="sm">{{ total }} downloads · page {{ page }} of {{ pageCount }}</MutedText>
    <div class="flex gap-2">
      <SecondaryButton :disabled="page <= 1" compact @click="page = page - 1">{{ t('ui.pager.previous') }}</SecondaryButton>
      <SecondaryButton :disabled="page >= pageCount" compact @click="page = page + 1">{{ t('ui.pager.next') }}</SecondaryButton>
    </div>
  </div>
</template>
