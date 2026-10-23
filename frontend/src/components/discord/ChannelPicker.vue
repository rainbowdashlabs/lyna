/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {type ChannelPurpose, type Directory, type DiscordChannel, listChannels} from '~/api/adminDiscord'

/**
 * A channel of the guild, picked by name from a list laid out like Discord's: by category, with the
 * threads of each channel under it. Without a bot there are no names to pick from, and the channel
 * is typed by id as it always was.
 *
 * <p>A channel saved earlier that is not in the list - deleted, or a thread archived long ago - stays
 * selected as "unknown channel" rather than silently becoming another one.
 */
const props = defineProps<{ guildId: string, purpose: ChannelPurpose, optional?: boolean }>()
const channelId = defineModel<string>({required: true})

const {t} = useI18n()
const directory = ref<Directory<DiscordChannel> | null>(null)

onMounted(async () => {
  directory.value = await listChannels(props.guildId, props.purpose).catch(() => ({connected: false, entries: []}))
})
</script>

<template>
  <div v-if="directory">
    <ChannelSelect
        v-if="directory.connected"
        v-model="channelId"
        :channels="directory.entries"
        :none-label="optional ? t('ui.discordPicker.none') : undefined"
        :placeholder="t('ui.discordPicker.chooseChannel')"
    />
    <template v-else>
      <TextInput v-model="channelId" inputmode="numeric" :placeholder="t('ui.discordPicker.channelId')"/>
      <MutedText class="mt-1 block" size="xs">{{ t('ui.discordPicker.noBot') }}</MutedText>
    </template>
  </div>
</template>
