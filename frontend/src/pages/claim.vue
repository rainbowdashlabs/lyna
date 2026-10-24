/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {redeemKey} from '~/api/account'
import {useSession} from '~/composables/useSession'
import {useAdminAction} from '~/composables/useAdminAction'
import {usePageTitle} from '~/composables/usePageTitle'
import {forgetClaim, keyFromHash, pendingClaim, rememberClaim} from '~/util/claim'

/**
 * Where a claim link lands: {@code /claim#<key>}.
 *
 * <p>The key comes after the {@code #}, which a browser never sends to a server. It is moved into the
 * tab's session storage and out of the address at once, so it survives signing up or in and is not
 * left in the history. Claiming always takes a click: a link somebody else sent must not attach their
 * key to whoever happens to be signed in.
 */
const {t} = useI18n()
usePageTitle(t('page.claim.title'))

const {account, hydrate} = useSession()
const key = ref<string | null>(null)
const ready = ref(false)
const claimedName = ref<string | null>(null)
const {busy, error, run} = useAdminAction(() => t('ui.redeemKey.couldNotRedeem'))

onMounted(async () => {
  const fromLink = keyFromHash(window.location.hash)
  if (fromLink) {
    rememberClaim(fromLink)
    history.replaceState(history.state, '', window.location.pathname)
  }
  key.value = fromLink ?? pendingClaim()
  await hydrate()
  ready.value = true
})

async function claim() {
  if (!key.value) return
  const result = await run(() => redeemKey(key.value!))
  if (!result) return
  claimedName.value = result.productName
  forgetClaim()
}
</script>

<template>
  <main class="mx-auto flex flex-1 max-w-md flex-col justify-center gap-4 p-6">
    <PageHeader>{{ t('page.claim.title') }}</PageHeader>
    <template v-if="ready">
      <MutedText v-if="!key" size="sm" tag="p">{{ t('page.claim.noKey') }}</MutedText>
      <template v-else-if="claimedName">
        <MutedText size="sm" tag="p" data-testid="claim-done">{{ t('ui.redeemKey.redeemed', {product: claimedName}) }}</MutedText>
        <NuxtLink class="text-primary hover:underline" to="/account/licenses">{{ t('page.claim.toLicenses') }}</NuxtLink>
      </template>
      <template v-else>
        <LabelledField :label="t('ui.redeemKey.key')">
          <CodeSnippet block class="break-all font-data" data-testid="claim-key">{{ key }}</CodeSnippet>
        </LabelledField>
        <template v-if="account">
          <PrimaryButton :disabled="busy" full-width @click="claim">{{ t('page.claim.claim') }}</PrimaryButton>
          <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
        </template>
        <template v-else>
          <MutedText size="sm" tag="p">{{ t('page.claim.signInFirst') }}</MutedText>
          <PrimaryButton full-width @click="navigateTo({path: '/signup', query: {next: '/claim'}})">
            {{ t('page.claim.signUp') }}
          </PrimaryButton>
          <SecondaryButton full-width @click="navigateTo({path: '/login', query: {next: '/claim'}})">
            {{ t('page.claim.signIn') }}
          </SecondaryButton>
        </template>
      </template>
    </template>
  </main>
</template>
