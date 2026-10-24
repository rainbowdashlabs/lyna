/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {dashboardSettings, setDashboardSettings} from '~/api/adminUpdateTokens'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * Whether a product hands out dashboard keys, and on what schedule: how often a server's key is traded
 * for its successor, how long a key may go unused before it expires, and how many keys a buyer may
 * hold. An account that only took the trial always holds one.
 */
const props = defineProps<{ guildId: string, productId: number }>()

const {t} = useI18n()
const enabled = ref(false)
const refreshDays = ref(7)
const expiryDays = ref(30)
const allowance = ref(10)
const {busy, done, error, run} = useAdminAction(() => t('ui.productDashboardKeys.couldNotSave'))

onMounted(async () => {
  const settings = await run(() => dashboardSettings(props.guildId, props.productId))
  if (!settings) return
  enabled.value = settings.enabled
  refreshDays.value = settings.refreshDays
  expiryDays.value = settings.expiryDays
  allowance.value = settings.allowance
  done.value = false
})

async function save() {
  await run(() => setDashboardSettings(props.guildId, props.productId, {
    enabled: enabled.value,
    refreshDays: refreshDays.value,
    expiryDays: expiryDays.value,
    allowance: allowance.value,
  }))
}
</script>

<template>
  <form :aria-label="t('ui.productDashboardKeys.title')" class="space-y-3" @submit.prevent="save">
    <CardHeader>{{ t('ui.productDashboardKeys.title') }}</CardHeader>
    <MutedText class="block" size="xs">{{ t('ui.productDashboardKeys.explain') }}</MutedText>
    <label class="flex items-center gap-2 text-sm">
      <CheckboxInput v-model="enabled"/> {{ t('ui.productDashboardKeys.enabled') }}
    </label>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
      <LabelledField :label="t('ui.productDashboardKeys.refreshDays')">
        <NumberInput v-model="refreshDays" :aria-label="t('ui.productDashboardKeys.refreshDays')"/>
      </LabelledField>
      <LabelledField :label="t('ui.productDashboardKeys.expiryDays')">
        <NumberInput v-model="expiryDays" :aria-label="t('ui.productDashboardKeys.expiryDays')"/>
      </LabelledField>
      <LabelledField :label="t('ui.productDashboardKeys.allowance')">
        <NumberInput v-model="allowance" :aria-label="t('ui.productDashboardKeys.allowance')"/>
      </LabelledField>
    </div>
    <div class="flex items-center gap-3">
      <PrimaryButton :disabled="busy" type="submit">{{ t('ui.productDashboardKeys.save') }}</PrimaryButton>
      <MutedText v-if="done" size="sm">{{ t('common.saved') }}</MutedText>
    </div>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </form>
</template>
