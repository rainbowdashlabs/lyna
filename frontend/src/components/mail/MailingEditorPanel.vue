/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {type MailBlock, type MailingTemplate, previewMailing, saveMailingBlocks, sendTestMail} from '~/api/admin'

const props = defineProps<{
    guildId: string
    template: MailingTemplate
    blocks: MailBlock[]
}>()

const emit = defineEmits<{
    close: []
    saved: []
}>()

const blocks = ref<MailBlock[]>(JSON.parse(JSON.stringify(props.blocks)) as MailBlock[])
const busy = ref(false)
const message = ref<string | null>(null)
const isError = ref(false)
const previewHtml = ref<string | null>(null)
const testAddress = ref('')

function report(e: unknown, fallback: string) {
  const error = e as {response?: {data?: string}}
  isError.value = true
  message.value = typeof error.response?.data === 'string' ? error.response.data : fallback
}

async function save() {
  busy.value = true
  message.value = null
  isError.value = false
  try {
    await saveMailingBlocks(props.guildId, props.template.id, blocks.value)
    message.value = 'Saved.'
    emit('saved')
  } catch (e) {
    report(e, 'Could not save the mail.')
  } finally {
    busy.value = false
  }
}

/**
 * Asks the backend what the mail looks like, rather than drawing an impression of it here. A preview
 * the editor rendered would be a second implementation of the mail, and the two would drift.
 */
async function preview() {
  busy.value = true
  message.value = null
  isError.value = false
  try {
    previewHtml.value = await previewMailing(props.guildId, props.template.id, blocks.value)
  } catch (e) {
    report(e, 'Could not render the preview.')
  } finally {
    busy.value = false
  }
}

async function sendTest() {
  busy.value = true
  message.value = null
  isError.value = false
  try {
    await sendTestMail(props.guildId, props.template.id, testAddress.value)
    message.value = `Sent to ${testAddress.value}.`
    testAddress.value = ''
  } catch (e) {
    report(e, 'Could not send it.')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="space-y-4">
    <header class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <SectionHeader>{{ template.productName }}</SectionHeader>
        <MutedText tag="div">{{ template.name }}</MutedText>
      </div>
      <SecondaryButton compact @click="emit('close')">Back to the list</SecondaryButton>
    </header>

    <MailBlockEditor v-model="blocks"/>

    <div class="flex flex-wrap items-center gap-2">
      <PrimaryButton :disabled="busy" @click="save">{{ busy ? 'Working…' : 'Save' }}</PrimaryButton>
      <SecondaryButton :disabled="busy" @click="preview">Preview</SecondaryButton>
      <MutedText v-if="message" :class="isError ? 'text-error' : 'text-success'" size="sm">{{ message }}</MutedText>
    </div>

    <section v-if="previewHtml">
      <CardHeader>Preview</CardHeader>
      <!--
        Sandboxed, and the document is assigned rather than written into the page. The raw block is
        operator HTML on purpose, and this is the one place it is rendered - it must not be able to
        reach the admin page around it.
      -->
      <iframe
          :srcdoc="previewHtml"
          class="h-[28rem] w-full rounded-theme border border-border-light dark:border-border-dark"
          sandbox=""
          title="What the mail looks like"
      />
    </section>

    <section>
      <CardHeader>Send yourself one</CardHeader>
      <MutedText class="mb-2 block" size="sm">
        With stand-in values. The only real check is what a mail client makes of it.
      </MutedText>
      <div class="flex flex-wrap items-end gap-2">
        <LabelledField class="flex-1" label="Address">
          <EmailInput v-model="testAddress" autocomplete="email"/>
        </LabelledField>
        <SecondaryButton :disabled="busy || !testAddress.trim()" @click="sendTest">Send</SecondaryButton>
      </div>
    </section>
  </div>
</template>
