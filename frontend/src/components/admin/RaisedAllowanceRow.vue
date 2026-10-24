/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref, watch} from 'vue'
import type {RaisedAllowance} from '~/api/adminUpdateTokens'

const {t} = useI18n()

const props = defineProps<{
    allowance: RaisedAllowance
    marker: number
    busy: boolean
}>()

defineEmits<{
    save: [granted: number]
}>()

const granted = ref(props.allowance.granted)
watch(() => props.allowance.granted, next => {
  granted.value = next
})
</script>

<template>
  <GutterRow :aria-label="allowance.account" :marker="marker" role="listitem">
    <div class="flex flex-wrap items-center gap-2">
      <KeyBadge>{{ allowance.account }}</KeyBadge>
      <span>{{ allowance.productName }}<template v-if="allowance.keyTypeName"> · {{ allowance.keyTypeName }}</template></span>
      <MutedText class="font-data" size="xs">{{ t('ui.raisedAllowanceRow.inUse', {used: allowance.used}) }}</MutedText>
    </div>
    <template #trailing>
      <form class="flex items-center gap-1" @submit.prevent="$emit('save', granted)">
        <MutedText size="xs">{{ t('ui.raisedAllowanceRow.extra') }}</MutedText>
        <NumberInput v-model="granted" :aria-label="t('ui.raisedAllowanceRow.extra')" class="w-20"/>
        <SecondaryButton :disabled="busy || granted < 0" compact type="submit">{{ t('common.save') }}</SecondaryButton>
      </form>
    </template>
  </GutterRow>
</template>
