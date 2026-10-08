/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {deleteGuildLicense, getLicenseAccess, type LicenseSummary, setLicenseAccess} from '~/api/admin'
import {RELEASE_TYPES, type ReleaseType} from '~/api/adminDownloads'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * A license: who holds it, which release types it reaches, and ending it. The release types are
 * fetched when the row is opened, deleting asks once more before it happens.
 */
const props = defineProps<{ guildId: string, license: LicenseSummary }>()
const emit = defineEmits<{ deleted: [] }>()

const {t} = useI18n()
const open = ref(false)
const access = ref<ReleaseType[] | null>(null)
const confirming = ref(false)
const {busy, error, run} = useAdminAction(() => t('ui.adminLicenseRow.couldNotSave'))

async function toggle() {
  open.value = !open.value
  if (open.value && access.value === null) {
    access.value = await run(() => getLicenseAccess(props.guildId, props.license.id)) ?? []
  }
}

async function flip(type: ReleaseType) {
  const current = access.value ?? []
  const next = current.includes(type) ? current.filter(entry => entry !== type) : [...current, type]
  const saved = await run(() => setLicenseAccess(props.guildId, props.license.id, next))
  if (saved) access.value = saved
}

async function remove() {
  await run(() => deleteGuildLicense(props.guildId, props.license.id))
  if (!error.value) emit('deleted')
}
</script>

<template>
  <li :aria-label="license.identifier" class="space-y-2 p-3 text-sm">
    <div class="flex items-center gap-3">
      <div class="min-w-0 flex-1">
        <div class="font-medium">{{ license.productName }}</div>
        <MutedText class="font-data" tag="div">
          #{{ license.id }} · {{ license.identifier }} · {{ license.owner ? t('ui.adminLicenseRow.owner', {owner: license.owner}) : t('ui.adminLicenseRow.unclaimed') }} · {{ t('ui.adminLicenseRow.sharees', {count: license.shareeCount}) }}
        </MutedText>
      </div>
      <SecondaryButton compact @click="toggle">{{ open ? t('ui.adminLicenseRow.close') : t('ui.adminLicenseRow.manage') }}</SecondaryButton>
    </div>
    <div v-if="open" class="flex flex-wrap items-center gap-4 border-t border-border-light pt-2 dark:border-border-dark">
      <SectionLabel>{{ t('ui.adminLicenseRow.reaches') }}</SectionLabel>
      <label v-for="type in RELEASE_TYPES" :key="type" class="font-data flex items-center gap-2 text-xs">
        <CompactToggle :disabled="busy || access === null" :model-value="access?.includes(type) ?? false" @update:model-value="flip(type)"/> {{ type }}
      </label>
      <ErrorButton v-if="!confirming" :disabled="busy" class="ml-auto" compact @click="confirming = true">{{ t('ui.adminLicenseRow.delete') }}</ErrorButton>
      <ErrorButton v-else :disabled="busy" class="ml-auto" compact @click="remove">{{ t('ui.adminLicenseRow.confirmDelete') }}</ErrorButton>
    </div>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </li>
</template>
