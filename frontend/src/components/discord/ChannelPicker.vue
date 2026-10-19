/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {type ChannelPurpose, type Directory, type DiscordChannel, listChannels} from '~/api/adminDiscord'

/**
 * A channel of the guild, picked by name. Threads are shown under the forum they belong to. Without
 * a bot there are no names to pick from, and the channel is typed by id as it always was.
 *
 * <p>A channel saved earlier that is not in the list - deleted, or a thread archived long ago - stays
 * selected as "unknown channel" rather than silently becoming another one.
 */
const props = defineProps<{ guildId: string, purpose: ChannelPurpose, optional?: boolean }>()
const channelId = defineModel<string>({required: true})

const {t} = useI18n()
const directory = ref<Directory<DiscordChannel> | null>(null)

const unknown = computed(() => !!channelId.value
    && !!directory.value?.connected
    && !directory.value.entries.some(channel => channel.id === channelId.value))

function label(channel: DiscordChannel): string {
  if (channel.kind === 'THREAD') return `${channel.parent} › ${channel.name}`
  if (channel.kind === 'FORUM') return t('ui.discordPicker.forum', {name: channel.name})
  return `#${channel.name}`
}

onMounted(async () => {
  directory.value = await listChannels(props.guildId, props.purpose).catch(() => ({connected: false, entries: []}))
})
</script>

<template>
  <div v-if="directory">
    <SelectInput v-if="directory.connected" v-model="channelId">
      <option :disabled="!optional" value="">{{ optional ? t('ui.discordPicker.none') : t('ui.discordPicker.chooseChannel') }}</option>
      <option v-if="unknown" :value="channelId">{{ t('ui.discordPicker.unknownChannel') }}</option>
      <option v-for="channel in directory.entries" :key="channel.id" :value="channel.id">{{ label(channel) }}</option>
    </SelectInput>
    <template v-else>
      <TextInput v-model="channelId" inputmode="numeric" :placeholder="t('ui.discordPicker.channelId')"/>
      <MutedText class="mt-1 block" size="xs">{{ t('ui.discordPicker.noBot') }}</MutedText>
    </template>
  </div>
</template>
