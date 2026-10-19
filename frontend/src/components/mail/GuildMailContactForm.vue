/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {type MailLink, saveGuildMailContact} from '~/api/admin'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * Where every purchase mail of a guild points: the Discord invite behind {{ discordUrl }}, and the
 * links the links block closes the mail with.
 */
const props = defineProps<{ guildId: string, discordUrl: string | null, links: MailLink[] }>()
const emit = defineEmits<{ saved: [] }>()

const {t} = useI18n()
const discord = ref(props.discordUrl ?? '')
const rows = ref<MailLink[]>(structuredClone(props.links))
const {busy, done, error, run} = useAdminAction(() => t('ui.guildMailContactForm.couldNotSave'))

function addRow() {
  rows.value = [...rows.value, {label: '', url: ''}]
}

function removeRow(index: number) {
  rows.value = rows.value.filter((_, at) => at !== index)
}

async function save() {
  const links = rows.value
      .map(row => ({label: row.label.trim(), url: row.url.trim()}))
      .filter(row => row.label || row.url)
  await run(() => saveGuildMailContact(props.guildId, discord.value.trim() || null, links))
  if (!error.value) emit('saved')
}
</script>

<template>
  <form class="space-y-3" @submit.prevent="save">
    <LabelledField :help="t('ui.guildMailContactForm.discordHelp')" :label="t('ui.guildMailContactForm.discord')">
      <TextInput v-model="discord" type="url"/>
    </LabelledField>
    <div class="space-y-2">
      <SectionLabel>{{ t('ui.guildMailContactForm.links') }}</SectionLabel>
      <MutedText class="block" size="xs">{{ t('ui.guildMailContactForm.linksHelp') }}</MutedText>
      <div v-for="(row, index) in rows" :key="index" class="grid grid-cols-[minmax(0,1fr)_minmax(0,2fr)_auto] items-end gap-2">
        <LabelledField :label="t('ui.guildMailContactForm.label')"><TextInput v-model="row.label"/></LabelledField>
        <LabelledField :label="t('ui.guildMailContactForm.address')"><TextInput v-model="row.url" type="url"/></LabelledField>
        <ErrorButton compact @click="removeRow(index)">{{ t('common.remove') }}</ErrorButton>
      </div>
      <SecondaryButton compact @click="addRow">{{ t('ui.guildMailContactForm.addLink') }}</SecondaryButton>
    </div>
    <div class="flex items-center gap-3">
      <PrimaryButton :disabled="busy" type="submit">{{ t('ui.guildMailContactForm.save') }}</PrimaryButton>
      <MutedText v-if="done" size="sm">{{ t('ui.guildMailContactForm.saved') }}</MutedText>
    </div>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </form>
</template>
