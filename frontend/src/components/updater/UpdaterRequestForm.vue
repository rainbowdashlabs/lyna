/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'

const {t} = useI18n()

defineProps<{
    busy: boolean
}>()

const emit = defineEmits<{
    submit: [amount: number, reason: string]
}>()

const amount = ref(5)
const reason = ref('')

function submit() {
  if (!reason.value.trim() || amount.value < 1) return
  emit('submit', amount.value, reason.value.trim())
}
</script>

<template>
  <form class="space-y-2" @submit.prevent="submit">
    <MutedText class="block" size="sm">{{ t('ui.updaterRequestForm.allUsed') }}</MutedText>
    <div class="grid grid-cols-1 gap-2 sm:grid-cols-[6rem_1fr_auto]">
      <LabelledField :label="t('ui.updaterRequestForm.amount')">
        <NumberInput v-model="amount"/>
      </LabelledField>
      <LabelledField :label="t('ui.updaterRequestForm.reason')">
        <TextInput v-model="reason" :placeholder="t('ui.updaterRequestForm.reasonPlaceholder')"/>
      </LabelledField>
      <div class="flex items-end">
        <PrimaryButton :disabled="busy || !reason.trim() || amount < 1" compact type="submit">
          {{ t('ui.updaterRequestForm.request') }}
        </PrimaryButton>
      </div>
    </div>
  </form>
</template>
