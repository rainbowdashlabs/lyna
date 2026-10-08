/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {createMailing, listMailings, type MailingTemplate, sendMailing} from '~/api/admin'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * A product's mailing: made here when it has none, written on the mailing page, and sent by hand to
 * somebody who bought the product elsewhere - which issues them a license, as {@code /mailing send}
 * does.
 */
const props = defineProps<{ guildId: string, productId: number }>()

const {t} = useI18n()
const mailing = ref<MailingTemplate | null>(null)
const loaded = ref(false)
const mailName = ref('')
const address = ref('')
const recipient = ref('')
const {busy, done, error, run} = useAdminAction(() => t('ui.productMailingTab.couldNotSend'))

async function load() {
  mailing.value = (await listMailings(props.guildId)).find(entry => entry.productId === props.productId) ?? null
  loaded.value = true
}

async function create() {
  await run(() => createMailing(props.guildId, props.productId, mailName.value.trim()))
  if (!error.value) await load()
}

async function send() {
  await run(() => sendMailing(props.guildId, props.productId, address.value.trim(), recipient.value.trim()))
  if (!error.value) {
    address.value = ''
    recipient.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div v-if="loaded" class="space-y-4">
    <section v-if="!mailing" class="space-y-3 border border-border-light p-4 dark:border-border-dark">
      <MutedText size="sm" tag="p">{{ t('ui.productMailingTab.none') }}</MutedText>
      <LabelledField :label="t('ui.productMailingTab.mailName')"><TextInput v-model="mailName"/></LabelledField>
      <PrimaryButton :disabled="busy || !mailName.trim()" @click="create">{{ t('ui.productMailingTab.create') }}</PrimaryButton>
    </section>
    <template v-else>
      <p class="text-sm">
        {{ t('ui.productMailingTab.exists', {name: mailing.name}) }}
        <NuxtLink :to="`/admin/g/${guildId}/mailing`" class="text-primary underline">{{ t('ui.productMailingTab.edit') }}</NuxtLink>
      </p>
      <section class="space-y-3 border border-border-light p-4 dark:border-border-dark">
        <CardHeader>{{ t('ui.productMailingTab.sendTitle') }}</CardHeader>
        <div class="grid gap-3 sm:grid-cols-2">
          <LabelledField :label="t('ui.productMailingTab.address')"><EmailInput v-model="address"/></LabelledField>
          <LabelledField :label="t('ui.productMailingTab.recipient')"><TextInput v-model="recipient"/></LabelledField>
        </div>
        <PrimaryButton :disabled="busy || !address.trim() || !recipient.trim()" @click="send">{{ t('ui.productMailingTab.send') }}</PrimaryButton>
        <MutedText v-if="done" size="sm">{{ t('ui.productMailingTab.sent') }}</MutedText>
      </section>
    </template>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </div>
</template>
