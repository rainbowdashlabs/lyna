/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {createKeyType, type KeyType, type KeyTypeDraft, productKeyTypes} from '~/api/adminUpdateTokens'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * The kinds of server key a product hands out. Each has a gateway secret of its own, and a gateway
 * holding it is answered about that type's keys only.
 */
const props = defineProps<{ guildId: string, productId: number }>()

const {t} = useI18n()
const types = ref<KeyType[]>([])
const fresh = (): KeyTypeDraft => ({
  slug: '', name: '', command: null, enabled: true, refreshDays: 7, expiryDays: 30, allowance: 10, trialAllowance: 1,
})
const draft = ref<KeyTypeDraft>(fresh())
const {busy, error, run} = useAdminAction(() => t('ui.productKeyTypesTab.couldNotCreate'))

onMounted(async () => {
  types.value = await productKeyTypes(props.guildId, props.productId)
})

async function create() {
  const created = await run(() => createKeyType(props.guildId, props.productId, draft.value))
  if (!created) return
  types.value = [...types.value, created]
  draft.value = fresh()
}

function replace(next: KeyType) {
  types.value = types.value.map(type => type.id === next.id ? next : type)
}
</script>

<template>
  <div class="space-y-6">
    <MutedText size="sm" tag="p">{{ t('ui.productKeyTypesTab.explain') }}</MutedText>
    <KeyTypeCard
        v-for="type in types"
        :key="type.id"
        :guild-id="guildId"
        :key-type="type"
        @changed="replace"
        @deleted="types = types.filter(other => other.id !== type.id)"
    />
    <EmptyHint v-if="!types.length">{{ t('ui.productKeyTypesTab.none') }}</EmptyHint>
    <form :aria-label="t('ui.productKeyTypesTab.add')" class="space-y-3 border border-border-light p-4 dark:border-border-dark" @submit.prevent="create">
      <CardHeader>{{ t('ui.productKeyTypesTab.add') }}</CardHeader>
      <KeyTypeFields v-model="draft"/>
      <PrimaryButton :disabled="busy" type="submit">{{ t('ui.productKeyTypesTab.create') }}</PrimaryButton>
      <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
    </form>
  </div>
</template>
