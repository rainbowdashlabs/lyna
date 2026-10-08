/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {
  getReleaseWebhook,
  issueReleaseWebhook,
  type ReleaseWebhook,
  removeReleaseWebhook,
  setReleaseChannel,
} from '~/api/admin'

/**
 * The GitHub webhook that announces a product's releases in a Discord channel: the address and secret
 * to give GitHub, and the channel to post in.
 */
const props = defineProps<{
  guildId: string
  productId: number
}>()

const {t} = useI18n()

const webhook = ref<ReleaseWebhook | null>(null)
const channelId = ref('')
const busy = ref(false)
const errorMessage = ref<string | null>(null)
const saved = ref(false)

/** Runs a change, saying what went wrong in the server's own words where it gave some. */
async function run(action: () => Promise<void>) {
  busy.value = true
  errorMessage.value = null
  saved.value = false
  try {
    await action()
  } catch (e) {
    const error = e as { response?: { data?: string } }
    errorMessage.value = typeof error.response?.data === 'string' && error.response.data
        ? error.response.data
        : t('ui.releaseWebhookPanel.couldNotSave')
  } finally {
    busy.value = false
  }
}

function show(next: ReleaseWebhook | null) {
  webhook.value = next
  channelId.value = next?.channelId ?? ''
}

const issue = () => run(async () => show(await issueReleaseWebhook(props.guildId, props.productId)))
const remove = () => run(async () => {
  await removeReleaseWebhook(props.guildId, props.productId)
  show(null)
})
const saveChannel = () => run(async () => {
  await setReleaseChannel(props.guildId, props.productId, channelId.value.trim() || null)
  saved.value = true
})

onMounted(() => run(async () => show(await getReleaseWebhook(props.guildId, props.productId))))
</script>

<template>
  <section class="space-y-3">
    <SectionLabel>{{ t('ui.releaseWebhookPanel.title') }}</SectionLabel>
    <template v-if="webhook">
      <ReleaseWebhookDetails :webhook="webhook"/>
      <LabelledField :label="t('ui.releaseWebhookPanel.channelId')">
        <TextInput v-model="channelId"/>
      </LabelledField>
      <div class="flex flex-wrap items-center gap-3">
        <PrimaryButton :disabled="busy" @click="saveChannel">{{ t('ui.releaseWebhookPanel.saveChannel') }}</PrimaryButton>
        <SecondaryButton :disabled="busy" @click="issue">{{ t('ui.releaseWebhookPanel.rotate') }}</SecondaryButton>
        <ErrorButton :disabled="busy" @click="remove">{{ t('ui.releaseWebhookPanel.remove') }}</ErrorButton>
        <MutedText v-if="saved" size="sm">{{ t('ui.releaseWebhookPanel.saved') }}</MutedText>
      </div>
    </template>
    <template v-else>
      <MutedText size="sm" tag="p">{{ t('ui.releaseWebhookPanel.explain') }}</MutedText>
      <SecondaryButton :disabled="busy" @click="issue">{{ t('ui.releaseWebhookPanel.create') }}</SecondaryButton>
    </template>
    <MutedText v-if="errorMessage" class="block text-error" size="sm">{{ errorMessage }}</MutedText>
  </section>
</template>
