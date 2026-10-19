/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {type Directory, type DiscordRole, listRoles} from '~/api/adminDiscord'

/**
 * A role of the guild, picked by name. Without a bot there are no names to pick from, and the role
 * is typed by id as it always was.
 *
 * <p>A role saved earlier that the guild no longer has stays selected as "unknown role" rather than
 * silently becoming another one.
 */
const props = defineProps<{ guildId: string, optional?: boolean }>()
const roleId = defineModel<string>({required: true})

const {t} = useI18n()
const directory = ref<Directory<DiscordRole> | null>(null)

const unknown = computed(() => !!roleId.value
    && !!directory.value?.connected
    && !directory.value.entries.some(role => role.id === roleId.value))

onMounted(async () => {
  directory.value = await listRoles(props.guildId).catch(() => ({connected: false, entries: []}))
})
</script>

<template>
  <div v-if="directory">
    <SelectInput v-if="directory.connected" v-model="roleId">
      <option :disabled="!optional" value="">{{ optional ? t('ui.discordPicker.none') : t('ui.discordPicker.chooseRole') }}</option>
      <option v-if="unknown" :value="roleId">{{ t('ui.discordPicker.unknownRole') }}</option>
      <option v-for="role in directory.entries" :key="role.id" :value="role.id">{{ role.name }}</option>
    </SelectInput>
    <template v-else>
      <TextInput v-model="roleId" inputmode="numeric" :placeholder="t('ui.discordPicker.roleId')"/>
      <MutedText class="mt-1 block" size="xs">{{ t('ui.discordPicker.noBot') }}</MutedText>
    </template>
  </div>
</template>
