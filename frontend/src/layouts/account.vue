/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {logout} from '~/api/account'
import {useSession} from '~/composables/useSession'
import type {Tab} from '~/components/chrome/TabStrip.vue'
import type {StatusItem} from '~/components/chrome/StatusBar.vue'

const {t} = useI18n()

const router = useRouter()
const {clear, account} = useSession()

/**
 * Ends the session here whatever the backend answers: the token is gone from this browser either
 * way, and a backend that could not be told is one the token expires out of on its own.
 */
async function doLogout() {
  await logout().catch(() => undefined)
  clear()
  await router.replace('/login')
}

const tabs = computed<Tab[]>(() => [
  {to: '/account', label: t('layout.account.overview'), exact: true},
  {to: '/account/licenses', label: t('layout.account.licenses')},
  {to: '/account/downloads', label: t('layout.account.downloads')},
  {to: '/account/security', label: t('layout.account.security')},
  {to: '/account/appearance', label: t('layout.account.appearance')},
])

const who = computed(() => account.value?.username
    ?? account.value?.email
    ?? t('layout.account.anonymous'))

/**
 * What the bar says about this session, rather than about the instance. An account area is not the
 * place to publish a schema version to whoever signs up.
 */
const status = computed<StatusItem[]>(() => [
  {label: who.value, tone: 'ok'},
  {label: account.value?.discordId ? 'discord' : t('layout.account.notLinked')},
])
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <TabStrip :action="t('auth.logout')" :tabs="tabs" @action="doLogout">
      <template #end>
        <NuxtLink
            class="font-data border-l border-border-light px-4 py-2.5 text-xs whitespace-nowrap text-(--text-muted) hover:text-(--text) dark:border-border-dark"
            to="/"
        >
          {{ t('layout.account.storefront') }}
        </NuxtLink>
      </template>
    </TabStrip>

    <main class="mx-auto w-full max-w-6xl flex-1 p-6">
      <slot/>
    </main>

    <StatusBar :left="status"/>
  </div>
</template>
