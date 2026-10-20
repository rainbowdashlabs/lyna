/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, ref} from 'vue'
import type {UpdateToken} from '~/api/updateTokens'
import {formatDateTime} from '~/util/format'

const {t} = useI18n()

const props = defineProps<{
    token: UpdateToken
    marker: number
}>()

const emit = defineEmits<{
    rename: [name: string]
    reissue: []
    revoke: []
}>()

const shown = ref(false)
const editing = ref(false)
const draft = ref('')
const confirming = ref<'reissue' | 'revoke' | null>(null)

const masked = computed(() => `${props.token.token.slice(0, 8)}${'•'.repeat(12)}`)

function startEditing() {
  draft.value = props.token.name
  editing.value = true
}

function saveName() {
  if (!draft.value.trim()) return
  emit('rename', draft.value.trim())
  editing.value = false
}

async function copy() {
  await navigator.clipboard.writeText(props.token.token)
}

/** Reissuing and revoking both break the updater using the token, so each takes a second click. */
function confirmOrAsk(action: 'reissue' | 'revoke') {
  if (confirming.value !== action) {
    confirming.value = action
    return
  }
  confirming.value = null
  if (action === 'reissue') emit('reissue')
  else emit('revoke')
}
</script>

<template>
  <GutterRow :aria-label="token.name" :marker="marker" role="listitem">
    <div class="space-y-1">
      <div v-if="editing" class="flex items-center gap-2">
        <TextInput v-model="draft" :placeholder="t('ui.updaterTokenRow.name')" @keyup.enter="saveName"/>
        <SecondaryButton compact @click="saveName">{{ t('common.save') }}</SecondaryButton>
      </div>
      <div v-else class="font-medium">{{ token.name }}</div>
      <div class="flex flex-wrap items-center gap-2">
        <KeyBadge class="break-all">{{ shown ? token.token : masked }}</KeyBadge>
        <SecondaryButton compact @click="shown = !shown">
          {{ shown ? t('ui.updaterTokenRow.hide') : t('ui.updaterTokenRow.show') }}
        </SecondaryButton>
        <SecondaryButton compact @click="copy">{{ t('ui.updaterTokenRow.copy') }}</SecondaryButton>
      </div>
      <MutedText class="font-data block" size="xs">
        {{ t('ui.updaterTokenRow.created', {at: formatDateTime(token.createdAt)}) }} ·
        {{ token.lastUsedAt ? t('ui.updaterTokenRow.lastUsed', {at: formatDateTime(token.lastUsedAt)}) : t('ui.updaterTokenRow.neverUsed') }}
      </MutedText>
      <MutedText v-if="token.lastReuseAt" class="block text-error" size="xs">
        {{ t('ui.updaterTokenRow.reused', {at: formatDateTime(token.lastReuseAt)}) }}
      </MutedText>
    </div>
    <template #trailing>
      <div class="flex flex-wrap justify-end gap-1">
        <SecondaryButton v-if="!editing" compact @click="startEditing">{{ t('ui.updaterTokenRow.rename') }}</SecondaryButton>
        <SecondaryButton compact @click="confirmOrAsk('reissue')">
          {{ confirming === 'reissue' ? t('ui.updaterTokenRow.reallyReissue') : t('ui.updaterTokenRow.reissue') }}
        </SecondaryButton>
        <SecondaryButton compact @click="confirmOrAsk('revoke')">
          {{ confirming === 'revoke' ? t('ui.updaterTokenRow.reallyRevoke') : t('common.revoke') }}
        </SecondaryButton>
      </div>
    </template>
  </GutterRow>
</template>
