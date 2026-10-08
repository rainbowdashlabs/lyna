/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {useRoute} from 'vue-router'
import {deleteDebugReport} from '~/api/debug'
import {usePageTitle} from '~/composables/usePageTitle'

/**
 * Deletes a debug report, at the address UpdateButler printed for it.
 *
 * <p>UpdateButler deleted on opening the link. Here it takes a click: a link posted in Discord is
 * opened by Discord itself to build its preview, which would delete the report the moment somebody
 * shared it.
 */
const {t} = useI18n()
usePageTitle(t('page.debug.delete.title'))
const route = useRoute()

const state = ref<'ask' | 'busy' | 'deleted' | 'unknown' | 'failed'>('ask')

async function remove() {
    state.value = 'busy'
    try {
        await deleteDebugReport(String(route.params.key))
        state.value = 'deleted'
    } catch (e) {
        const status = (e as { response?: { status?: number } }).response?.status
        state.value = status === 404 ? 'unknown' : 'failed'
    }
}
</script>

<template>
  <div class="flex flex-1 flex-col">
    <main class="mx-auto w-full max-w-xl flex-1 space-y-4 px-4 py-12">
      <Heading :level="1">{{ t('page.debug.delete.title') }}</Heading>
      <template v-if="state === 'ask' || state === 'busy'">
        <p>{{ t('page.debug.delete.explain') }}</p>
        <ErrorButton :disabled="state === 'busy'" @click="remove">{{ t('page.debug.delete.confirm') }}</ErrorButton>
      </template>
      <Alert v-else-if="state === 'deleted'" variant="success">{{ t('page.debug.delete.deleted') }}</Alert>
      <Alert v-else-if="state === 'unknown'" variant="error">{{ t('page.debug.delete.unknown') }}</Alert>
      <Alert v-else variant="error">{{ t('page.debug.delete.failed') }}</Alert>
    </main>
  </div>
</template>
