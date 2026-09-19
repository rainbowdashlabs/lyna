/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'

const {t} = useI18n()
const router = useRouter()
const {setToken, hydrate} = useSession()

const failed = ref(false)

/** Only a path on this site: anything else would let a crafted link send somebody elsewhere. */
function localPath(raw: string | null): string {
  return raw && raw.startsWith('/') && !raw.startsWith('//') ? raw : '/account'
}

onMounted(async () => {
  const fragment = new URLSearchParams(window.location.hash.slice(1))
  const token = fragment.get('token')
  history.replaceState(null, '', window.location.pathname)
  if (!token) {
    failed.value = true
    return
  }
  setToken(token)
  await hydrate()
  await router.replace(localPath(fragment.get('next')))
})
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-md flex-col justify-center p-6">
    <PageHeader class="mb-6">
      {{ t('page.auth-discord.signingIn') }}
    </PageHeader>
    <Alert v-if="failed" variant="error">
      {{ t('page.auth-discord.nothingToSignInWith') }}
      <NuxtLink class="ml-1 text-primary hover:underline" to="/login">{{ t('page.auth-discord.backToSignIn') }}</NuxtLink>
    </Alert>
    <Spinner v-else size="lg"/>
  </main>
</template>
