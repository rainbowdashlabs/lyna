/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref, watch} from 'vue'
import {matchingZones, type RenameableChannel, setTimeChannel} from '~/api/adminTimeChannels'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * Putting a clock on a channel: which channel, which zone, and what the name says around the time.
 */
const props = defineProps<{ guildId: string, channels: RenameableChannel[] }>()
const emit = defineEmits<{ saved: [] }>()

const {t} = useI18n()
const channelId = ref('')
const zone = ref('')
const template = ref('')
const zones = ref<string[]>([])
const {busy, error, run} = useAdminAction(() => t('ui.timeChannelForm.couldNotSave'))

watch(zone, async typed => {
  zones.value = typed.length >= 2 ? await matchingZones(props.guildId, typed).catch(() => []) : []
})

async function save() {
  await run(() => setTimeChannel(props.guildId, channelId.value, zone.value.trim(), template.value.trim() || null))
  if (!error.value) emit('saved')
}
</script>

<template>
  <section class="space-y-3 border border-border-light p-4 dark:border-border-dark">
    <CardHeader>{{ t('ui.timeChannelForm.title') }}</CardHeader>
    <div class="grid gap-3 sm:grid-cols-3">
      <LabelledField :label="t('ui.timeChannelForm.channel')">
        <SelectInput v-model="channelId">
          <option disabled value="">{{ t('ui.timeChannelForm.choose') }}</option>
          <option v-for="channel in channels" :key="channel.id" :value="channel.id">{{ channel.name }}</option>
        </SelectInput>
      </LabelledField>
      <LabelledField :label="t('ui.timeChannelForm.zone')">
        <TextInput v-model="zone" :placeholder="t('ui.timeChannelForm.zonePlaceholder')" list="time-zones"/>
        <datalist id="time-zones"><option v-for="entry in zones" :key="entry" :value="entry"/></datalist>
      </LabelledField>
      <LabelledField :help="t('ui.timeChannelForm.templateHint')" :label="t('ui.timeChannelForm.template')">
        <TextInput v-model="template" :placeholder="t('ui.timeChannelForm.templatePlaceholder')"/>
      </LabelledField>
    </div>
    <PrimaryButton :disabled="busy || !channelId || !zone.trim()" @click="save">{{ t('ui.timeChannelForm.save') }}</PrimaryButton>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </section>
</template>
