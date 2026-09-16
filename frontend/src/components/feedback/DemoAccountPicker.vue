/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {demoLogin} from '~/api/demo'
import {useDemo} from '~/composables/useDemo'
import {useSession} from '~/composables/useSession'

const router = useRouter()
const {accounts, load} = useDemo()
const {setToken, hydrate} = useSession()

const busy = ref<string | null>(null)
const errorMessage = ref<string | null>(null)

onMounted(load)

/**
 * Signs in as one of the seeded accounts. Whoever is looking at a demo is picking a part to play,
 * not remembering an address, so the list offers the part rather than asking for the address.
 */
async function signInAs(email: string) {
  busy.value = email
  errorMessage.value = null
  try {
    const {token} = await demoLogin(email)
    setToken(token)
    await hydrate()
    await router.replace('/account')
  } catch (e) {
    const error = e as {response?: {data?: string}}
    errorMessage.value = typeof error.response?.data === 'string'
        ? error.response.data
        : 'Could not sign in as that account.'
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <section v-if="accounts && accounts.length" class="rounded-theme border border-info/40 bg-info/10 p-4">
    <CardHeader>Sign in as</CardHeader>
    <MutedText class="mb-3 block" size="sm">
      This is a demo instance. Pick somebody to look at it as &mdash; no password needed.
    </MutedText>
    <ul class="space-y-2">
      <li v-for="account in accounts" :key="account.email">
        <ChoiceButton
            :detail="account.description"
            :disabled="busy !== null"
            :title="account.role"
            class="capitalize"
            @click="signInAs(account.email)"
        />
      </li>
    </ul>
    <MutedText v-if="errorMessage" class="mt-2 block text-error" size="sm">{{ errorMessage }}</MutedText>
  </section>
</template>
