/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {createKofi, type KofiMapping, listKofi, removeKofiMapping} from '~/api/admin'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * The Ko-fi shop items that sell this product, by their direct-link code. A Ko-fi order naming one
 * issues a licence for this product, and the first is where the storefront's buy button points.
 */
const props = defineProps<{ guildId: string, productId: number }>()

const {t} = useI18n()
const codes = ref<KofiMapping[]>([])
const linkCode = ref('')
const {busy, error, run} = useAdminAction(() => t('ui.productKofiCodes.couldNotSave'))

async function load() {
  codes.value = (await listKofi(props.guildId)).filter(mapping => mapping.productId === props.productId)
}

async function add() {
  await run(() => createKofi(props.guildId, linkCode.value.trim(), props.productId))
  if (!error.value) linkCode.value = ''
  await load()
}

async function remove(code: string) {
  await run(() => removeKofiMapping(props.guildId, code))
  await load()
}

onMounted(load)
</script>

<template>
  <section class="space-y-3">
    <CardHeader>{{ t('ui.productKofiCodes.title') }}</CardHeader>
    <MutedText class="block" size="xs">{{ t('ui.productKofiCodes.explain') }}</MutedText>
    <ul v-if="codes.length" class="space-y-1 text-sm">
      <li v-for="mapping in codes" :key="mapping.linkCode" class="flex items-center justify-between gap-2">
        <code class="font-data">{{ mapping.linkCode }}</code>
        <ErrorButton :disabled="busy" compact @click="remove(mapping.linkCode)">{{ t('common.remove') }}</ErrorButton>
      </li>
    </ul>
    <MutedText v-else class="block" size="sm">{{ t('ui.productKofiCodes.none') }}</MutedText>
    <form class="flex flex-wrap items-end gap-2" @submit.prevent="add">
      <LabelledField class="flex-1" :label="t('ui.productKofiCodes.linkCode')">
        <TextInput v-model="linkCode"/>
      </LabelledField>
      <PrimaryButton :disabled="busy || !linkCode.trim()" type="submit">{{ t('ui.productKofiCodes.add') }}</PrimaryButton>
    </form>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </section>
</template>
