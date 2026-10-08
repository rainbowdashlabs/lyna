/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {
  type GuildRole,
  grantRoleAccess,
  listGuildRoles,
  listRoleAccess,
  RELEASE_TYPES,
  type ReleaseType,
  revokeRoleAccess,
  type RoleAccess,
} from '~/api/adminDownloads'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * Which Discord roles may download which release type of a product: what {@code /downloads roles}
 * manages. With a bot connected roles are picked by name; without one, typed by id.
 */
const props = defineProps<{ guildId: string, productId: number }>()

const {t} = useI18n()
const access = ref<RoleAccess[]>([])
const roles = ref<GuildRole[]>([])
const roleId = ref('')
const releaseType = ref<ReleaseType>('STABLE')
const {busy, error, run} = useAdminAction(() => t('ui.productAccessTab.couldNotSave'))

async function load() {
  [access.value, roles.value] = await Promise.all([
    listRoleAccess(props.guildId, props.productId),
    listGuildRoles(props.guildId),
  ])
}

async function grant() {
  await run(() => grantRoleAccess(props.guildId, props.productId, roleId.value.trim(), releaseType.value))
  if (!error.value) await load()
}

async function revoke(entry: RoleAccess) {
  await run(() => revokeRoleAccess(props.guildId, props.productId, entry.roleId, entry.releaseType))
  if (!error.value) await load()
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <EmptyHint v-if="!access.length">{{ t('ui.productAccessTab.none') }}</EmptyHint>
    <ul v-else class="divide-y divide-border-light border border-border-light dark:divide-border-dark dark:border-border-dark">
      <li v-for="entry in access" :key="`${entry.roleId}-${entry.releaseType}`" :aria-label="entry.roleName ?? entry.roleId" class="flex items-center gap-3 p-3">
        <span class="font-data">{{ entry.roleName ?? entry.roleId }}</span>
        <span class="font-data border border-current px-1 text-[10px]">{{ entry.releaseType }}</span>
        <ErrorButton :disabled="busy" class="ml-auto" compact @click="revoke(entry)">{{ t('ui.productAccessTab.revoke') }}</ErrorButton>
      </li>
    </ul>
    <section class="grid items-end gap-3 border border-border-light p-4 sm:grid-cols-[2fr_1fr_auto] dark:border-border-dark">
      <LabelledField :label="t('ui.productAccessTab.role')">
        <SelectInput v-if="roles.length" v-model="roleId">
          <option disabled value="">{{ t('ui.productAccessTab.choose') }}</option>
          <option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</option>
        </SelectInput>
        <TextInput v-else v-model="roleId" :placeholder="t('ui.productAccessTab.roleId')"/>
      </LabelledField>
      <LabelledField :label="t('ui.productAccessTab.releaseType')">
        <SelectInput v-model="releaseType">
          <option v-for="type in RELEASE_TYPES" :key="type" :value="type">{{ type }}</option>
        </SelectInput>
      </LabelledField>
      <PrimaryButton :disabled="busy || !roleId.trim()" @click="grant">{{ t('ui.productAccessTab.grant') }}</PrimaryButton>
    </section>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </div>
</template>
