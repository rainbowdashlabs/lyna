/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {createDownloadType, RELEASE_TYPES, type ReleaseType} from '~/api/adminDownloads'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * Making a download type. Its release type is chosen here once and kept.
 */
const props = defineProps<{ guildId: string }>()
const emit = defineEmits<{ created: [] }>()

const {t} = useI18n()
const name = ref('')
const description = ref('')
const releaseType = ref<ReleaseType>('STABLE')
const {busy, error, run} = useAdminAction(() => t('ui.downloadTypeCreate.couldNotCreate'))

async function create() {
  const made = await run(() => createDownloadType(props.guildId, {
    name: name.value, description: description.value, releaseType: releaseType.value,
  }))
  if (!made) return
  name.value = ''
  description.value = ''
  emit('created')
}
</script>

<template>
  <section class="space-y-3 border border-border-light p-4 dark:border-border-dark">
    <CardHeader>{{ t('ui.downloadTypeCreate.title') }}</CardHeader>
    <div class="grid gap-3 sm:grid-cols-3">
      <LabelledField :label="t('ui.downloadTypeCreate.name')"><TextInput v-model="name"/></LabelledField>
      <LabelledField :label="t('ui.downloadTypeCreate.description')"><TextInput v-model="description"/></LabelledField>
      <LabelledField :label="t('ui.downloadTypeCreate.releaseType')">
        <SelectInput v-model="releaseType">
          <option v-for="type in RELEASE_TYPES" :key="type" :value="type">{{ type }}</option>
        </SelectInput>
      </LabelledField>
    </div>
    <PrimaryButton :disabled="busy || !name.trim()" @click="create">{{ t('ui.downloadTypeCreate.create') }}</PrimaryButton>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </section>
</template>
