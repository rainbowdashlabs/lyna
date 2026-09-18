/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useAdminGuilds} from '~/composables/useAdminGuilds'
import {getInstanceSystem, type SystemInfo} from '~/api/admin'
import type {Tab} from '~/components/chrome/TabStrip.vue'
import type {StatusItem} from '~/components/chrome/StatusBar.vue'
import {ref} from 'vue'

const {t} = useI18n()

const route = useRoute()
const router = useRouter()
const {guilds, load} = useAdminGuilds()

const system = ref<SystemInfo | null>(null)

onMounted(async () => {
  await load()
  // Operator-only, so a failure here is somebody who administers a guild but not the instance.
  system.value = await getInstanceSystem().catch(() => null)
})

const currentGuildId = computed(() => {
  const match = /^\/admin\/g\/([^/]+)/.exec(route.path)
  return match?.[1] ?? null
})

const currentGuild = computed(() => guilds.value.find(g => g.id === currentGuildId.value) ?? null)
const isOperator = computed(() => guilds.value.some(g => g.role === 'operator'))
const inInstance = computed(() => route.path.startsWith('/admin/instance'))

const sections = computed<Tab[]>(() => {
  if (inInstance.value) {
    return [
      {to: '/admin/instance/system', label: t('layout.admin.system')},
      {to: '/admin/instance/appearance', label: t('layout.admin.appearance')},
      {to: '/admin/instance/operators', label: t('layout.admin.operators')},
    ]
  }
  if (!currentGuildId.value) return []
  const base = `/admin/g/${currentGuildId.value}`
  return [
    {to: `${base}/products`, label: t('layout.admin.products')},
    {to: `${base}/licenses`, label: t('layout.admin.licenses')},
    {to: `${base}/registrations`, label: t('layout.admin.registrations')},
    {to: `${base}/trial`, label: t('layout.admin.trial')},
    {to: `${base}/kofi`, label: t('layout.admin.kofi')},
    {to: `${base}/mailing`, label: t('layout.admin.mailing')},
    {to: `${base}/settings`, label: t('layout.admin.settings')},
  ]
})

/**
 * The instance, not the session: an operator reading this is the person who would act on it. The bot
 * being absent is stated outright rather than left to be inferred from a guild count of zero.
 */
const status = computed<StatusItem[]>(() => {
  if (!system.value) return [{label: t('layout.admin.guildAdmin')}]
  return [
    system.value.botConnected
        ? {label: t('layout.admin.botConnected'), tone: 'ok'}
        : {label: t('layout.admin.noBot'), tone: 'pending'},
    {label: t('layout.admin.guildsCount', {count: system.value.guildCount})},
  ]
})

function switchGuild(event: Event) {
  const target = event.target as HTMLSelectElement
  const id = target.value
  if (!id) return
  const tail = route.path.replace(/^\/admin\/g\/[^/]+/, '')
  router.push(`/admin/g/${id}${tail || '/products'}`)
}
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <TabStrip :tabs="sections">
      <template #end>
        <NuxtLink
            v-if="isOperator"
            to="/admin/instance/system"
            class="font-data px-4 py-2.5 text-xs whitespace-nowrap text-(--text-muted) hover:text-(--text)"
        >
          {{ t('layout.admin.instanceArea') }}
        </NuxtLink>
      </template>
    </TabStrip>

    <div class="flex flex-wrap items-center gap-3 border-b border-border-light px-4 py-2 dark:border-border-dark">
      <span class="font-data text-[11px] tracking-wider text-(--text-muted) uppercase">
        {{ t('layout.admin.administering') }}
      </span>
      <SelectInput
          v-if="guilds.length > 0"
          :model-value="currentGuildId ?? ''"
          class="max-w-xs"
          @change="switchGuild"
      >
        <option disabled value="">{{ t('layout.admin.selectAGuild') }}</option>
        <option v-for="g in guilds" :key="g.id" :value="g.id">
          {{ g.name }}{{ g.role === 'operator' ? ' (operator)' : '' }}
        </option>
      </SelectInput>
      <MutedText v-else size="sm">{{ t('layout.admin.noAdminGuildsAvailable') }}</MutedText>
    </div>

    <main class="mx-auto w-full max-w-6xl flex-1 p-6">
      <slot/>
    </main>

    <StatusBar
        :left="status"
        :right="system ? [{label: `lyna ${system.version}`}] : []"
    />
  </div>
</template>
