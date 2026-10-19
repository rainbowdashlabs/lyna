/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, ref} from 'vue'
import type {MailBlock} from '~/api/admin'

const {t} = useI18n()

/**
 * Edits one purchase mail: a product's own, or the guild's default every product without one sends.
 * What happens on save, preview and test is handed in, so both are edited the same way.
 *
 * <p>The preview is a sandboxed frame given the document rather than markup written into the page.
 * The raw block is operator HTML on purpose, and this is the one place it is rendered - it must not
 * be able to reach the admin page around it.
 */
const props = defineProps<{
    title: string
    subtitle?: string
    blocks: MailBlock[]
    save: (blocks: MailBlock[]) => Promise<void>
    preview: (blocks: MailBlock[]) => Promise<string>
    sendTest: (address: string) => Promise<void>
    /** A mail that can be loaded into the editor as a starting point, with what to call it. */
    starter?: {label: string; blocks: MailBlock[]}
    /** What each placeholder stands for, shown so it is clear which links will be left out. */
    values?: Record<string, string>
}>()

const emit = defineEmits<{
    close: []
    saved: []
}>()

const blocks = ref<MailBlock[]>(structuredClone(props.blocks))
const busy = ref(false)
const message = ref<string | null>(null)
const isError = ref(false)
const previewHtml = ref<string | null>(null)
const testAddress = ref('')
const confirmingLoad = ref(false)

const placeholders = computed(() => Object.entries(props.values ?? {})
    .filter(([name]) => name !== 'name' && name !== 'key')
    .map(([name, value]) => ({name, written: `{{ ${name} }}`, value})))

function report(e: unknown, fallback: string) {
  const error = e as {response?: {data?: string}}
  isError.value = true
  message.value = typeof error.response?.data === 'string' ? error.response.data : fallback
}

async function act(work: () => Promise<void>, fallback: string) {
  busy.value = true
  message.value = null
  isError.value = false
  try {
    await work()
  } catch (e) {
    report(e, fallback)
  } finally {
    busy.value = false
  }
}

async function save() {
  await act(async () => {
    await props.save(blocks.value)
    message.value = t('ui.mailingEditorPanel.saved')
    emit('saved')
  }, t('ui.mailingEditorPanel.couldNotSave'))
}

/**
 * Asks the backend what the mail looks like, rather than drawing an impression of it here. A preview
 * the editor rendered would be a second implementation of the mail, and the two would drift.
 */
async function showPreview() {
  await act(async () => {
    previewHtml.value = await props.preview(blocks.value)
  }, t('ui.mailingEditorPanel.couldNotPreview'))
}

async function sendTest() {
  const address = testAddress.value
  await act(async () => {
    await props.sendTest(address)
    message.value = t('ui.mailingEditorPanel.sentTo', {address})
    testAddress.value = ''
  }, t('ui.mailingEditorPanel.couldNotSend'))
}

/**
 * Replaces what the editor holds with the starter. Asks once first when that would throw blocks
 * away: nothing is saved until the operator says so, but an editor emptied by accident is still
 * work lost.
 */
function loadStarter() {
  if (!props.starter) return
  if (blocks.value.length > 0 && !confirmingLoad.value) {
    confirmingLoad.value = true
    return
  }
  blocks.value = structuredClone(props.starter.blocks)
  confirmingLoad.value = false
}
</script>

<template>
  <div class="space-y-4">
    <header class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <SectionHeader>{{ title }}</SectionHeader>
        <MutedText v-if="subtitle" tag="div">{{ subtitle }}</MutedText>
      </div>
      <div class="flex flex-wrap gap-2">
        <slot name="actions"/>
        <SecondaryButton v-if="starter" compact @click="loadStarter">
          {{ confirmingLoad ? t('ui.mailingEditorPanel.replaceBlocks') : starter.label }}
        </SecondaryButton>
        <SecondaryButton compact @click="emit('close')">{{ t('ui.mailingEditorPanel.backToTheList') }}</SecondaryButton>
      </div>
    </header>

    <MailBlockEditor v-model="blocks"/>

    <section v-if="placeholders.length" class="space-y-1">
      <CardHeader>{{ t('ui.mailingEditorPanel.placeholders') }}</CardHeader>
      <MutedText class="block" size="xs">{{ t('ui.mailingEditorPanel.placeholdersExplain') }}</MutedText>
      <dl class="font-data grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1 text-xs">
        <template v-for="placeholder in placeholders" :key="placeholder.name">
          <dt v-text="placeholder.written"/>
          <dd class="truncate" :class="placeholder.value ? '' : 'text-(--text-muted)'">{{ placeholder.value || t('ui.mailingEditorPanel.unset') }}</dd>
        </template>
      </dl>
    </section>

    <div class="flex flex-wrap items-center gap-2">
      <PrimaryButton :disabled="busy" @click="save">{{ busy ? t('ui.mailingEditorPanel.working') : t('ui.mailingEditorPanel.save') }}</PrimaryButton>
      <SecondaryButton :disabled="busy" @click="showPreview">{{ t('ui.mailingEditorPanel.preview') }}</SecondaryButton>
      <MutedText v-if="message" :class="isError ? 'text-error' : 'text-success'" size="sm">{{ message }}</MutedText>
    </div>

    <section v-if="previewHtml">
      <CardHeader>{{ t('ui.mailingEditorPanel.preview') }}</CardHeader>
      <iframe
          :srcdoc="previewHtml"
          class="h-[28rem] w-full rounded-theme border border-border-light dark:border-border-dark"
          sandbox=""
          :title="t('ui.mailingEditorPanel.whatTheMailLooksLike')"
      />
    </section>

    <section>
      <CardHeader>{{ t('ui.mailingEditorPanel.sendYourselfOne') }}</CardHeader>
      <MutedText class="mb-2 block" size="sm">
        {{ t('ui.mailingEditorPanel.withStandInValuesTheOnly') }}
      </MutedText>
      <div class="flex flex-wrap items-end gap-2">
        <LabelledField class="flex-1" :label="t('ui.mailingEditorPanel.address')">
          <EmailInput v-model="testAddress" autocomplete="email"/>
        </LabelledField>
        <SecondaryButton :disabled="busy || !testAddress.trim()" @click="sendTest">{{ t('ui.mailingEditorPanel.send') }}</SecondaryButton>
      </div>
    </section>
  </div>
</template>
