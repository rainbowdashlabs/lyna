/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {deleteKeyType, type KeyType, type KeyTypeDraft, replaceKeyTypeSecret, updateKeyType} from '~/api/adminUpdateTokens'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * One key type of a product: its settings, and the gateway secret that opens its keys and no others.
 * Replacing the secret shuts out every gateway holding the old one; deleting the type ends every key of
 * it, so both ask twice.
 */
const props = defineProps<{ guildId: string, keyType: KeyType }>()
const emit = defineEmits<{ changed: [keyType: KeyType], deleted: [] }>()

const {t} = useI18n()
const draft = ref<KeyTypeDraft>({...props.keyType})
const secret = ref(props.keyType.gatewaySecret)
const confirming = ref<'secret' | 'delete' | null>(null)
const {busy, done, error, run} = useAdminAction(() => t('ui.keyTypeCard.couldNotSave'))

async function save() {
  const saved = await run(() => updateKeyType(props.guildId, props.keyType.id, draft.value))
  if (saved) emit('changed', saved)
}

async function newSecret() {
  confirming.value = null
  const replaced = await run(() => replaceKeyTypeSecret(props.guildId, props.keyType.id))
  if (replaced) secret.value = replaced.gatewaySecret
}

async function remove() {
  confirming.value = null
  const removed = await run(async () => {
    await deleteKeyType(props.guildId, props.keyType.id)
    return true
  })
  if (removed) emit('deleted')
}
</script>

<template>
  <form :aria-label="keyType.name" class="space-y-3 border border-border-light p-4 dark:border-border-dark" @submit.prevent="save">
    <CardHeader>{{ keyType.name }}</CardHeader>
    <KeyTypeFields v-model="draft"/>
    <LabelledField :label="t('ui.keyTypeCard.secret')">
      <CodeSnippet block class="break-all select-all" data-testid="key-type-secret">{{ secret }}</CodeSnippet>
      <MutedText size="xs" tag="p">{{ t('ui.keyTypeCard.secretHint') }}</MutedText>
    </LabelledField>
    <div class="flex flex-wrap items-center gap-3">
      <PrimaryButton :disabled="busy" type="submit">{{ t('common.save') }}</PrimaryButton>
      <ConfirmButton v-if="confirming === 'secret'" @click="newSecret"/>
      <SecondaryButton v-else :disabled="busy" @click="confirming = 'secret'">{{ t('ui.keyTypeCard.newSecret') }}</SecondaryButton>
      <ConfirmButton v-if="confirming === 'delete'" @click="remove"/>
      <ErrorButton v-else :disabled="busy" @click="confirming = 'delete'">{{ t('ui.keyTypeCard.delete') }}</ErrorButton>
      <MutedText v-if="done" size="sm">{{ t('common.saved') }}</MutedText>
    </div>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </form>
</template>
