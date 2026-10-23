/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {listChannels} from '~/api/adminDiscord'
import {listTimeChannels, removeTimeChannel, type TimeChannel} from '~/api/adminTimeChannels'
import {usePageTitle} from '~/composables/usePageTitle'
import ConfirmButton from '~/components/button/ConfirmButton.vue'

/**
 * Channels whose name shows the time somewhere, renamed every quarter of an hour - what
 * {@code /timechannel} manages. The bot does the renaming, so without one nothing can be set up.
 */
definePageMeta({layout: 'admin'})

const {t} = useI18n()
usePageTitle(t('layout.admin.timeChannels'))
const guildId = String(useRoute().params.guildId)

const channels = ref<TimeChannel[]>([])
const botConnected = ref(true)
const loading = ref(true)
const error = ref('')
const confirming = ref<string | null>(null)

async function load() {
  try {
    const [listed, renameable] = await Promise.all([listTimeChannels(guildId), listChannels(guildId, 'renaming')])
    channels.value = listed
    botConnected.value = renameable.connected
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

/** Stops a channel once that has been confirmed; it keeps the name it was last given. */
async function remove(channelId: string) {
  confirming.value = null
  await removeTimeChannel(guildId, channelId)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="space-y-6">
    <PageHeader>{{ t('layout.admin.timeChannels') }}</PageHeader>
    <MutedText size="sm" tag="p">{{ t('page.admin.g.guildId.timeChannels.explain') }}</MutedText>
    <AsyncSection :error="error" :loading="loading">
      <EmptyHint v-if="!channels.length">{{ t('page.admin.g.guildId.timeChannels.none') }}</EmptyHint>
      <ul v-else class="divide-y divide-border-light border border-border-light dark:divide-border-dark dark:border-border-dark">
        <li v-for="channel in channels" :key="channel.channelId" :aria-label="channel.channelName ?? channel.channelId" class="flex items-center gap-3 p-3 text-sm">
          <div class="min-w-0 flex-1">
            <div class="font-data">{{ channel.shows }}</div>
            <MutedText class="font-data" tag="div">{{ channel.channelName ?? channel.channelId }} · {{ channel.zone }}</MutedText>
          </div>
          <div v-if="confirming === channel.channelId" class="flex items-center gap-2">
            <ConfirmButton @click="remove(channel.channelId)"/>
            <SecondaryButton compact @click="confirming = null">{{ t('common.cancel') }}</SecondaryButton>
          </div>
          <ErrorButton v-else compact @click="confirming = channel.channelId">{{ t('page.admin.g.guildId.timeChannels.remove') }}</ErrorButton>
        </li>
      </ul>
      <Alert v-if="!botConnected" class="mt-4" variant="info">{{ t('page.admin.g.guildId.timeChannels.needsBot') }}</Alert>
      <TimeChannelForm v-else :guild-id="guildId" class="mt-4" @saved="load"/>
    </AsyncSection>
  </div>
</template>
