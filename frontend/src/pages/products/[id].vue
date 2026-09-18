/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {getProduct, type KioskProductDetail} from '~/api/kiosk'
import {useSession} from '~/composables/useSession'

const {t} = useI18n()
const route = useRoute()
const {account, hydrate} = useSession()

const product = ref<KioskProductDetail | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const wizardOpen = ref(false)

const signedIn = computed(() => account.value !== null)

/**
 * Resolves who is looking before drawing the product, the same way the storefront does: the badge
 * says `Owned` only when the catalogue call carried a token.
 */
async function load() {
  const id = Number(route.params.id)
  if (!Number.isInteger(id)) {
    errorMessage.value = t('page.products.id.noSuchProduct')
    loading.value = false
    return
  }
  try {
    await hydrate()
    product.value = await getProduct(id)
  } catch (e) {
    const status = (e as { response?: { status?: number } }).response?.status
    errorMessage.value = status === 404
        ? t('page.products.id.noSuchProduct')
        : (e as Error).message ?? t('page.products.id.couldNotLoad')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <main class="flex-1 pb-12">
    <TabStrip :tabs="[{to: '/', label: t('kiosk.title'), exact: true}]">
      <template #end>
        <NuxtLink
            :to="signedIn ? '/account' : '/login'"
            class="font-data px-4 py-2.5 text-xs whitespace-nowrap text-(--text-muted) hover:text-(--text)"
        >
          {{ signedIn ? t('kiosk.yourAccount') : t('kiosk.signIn') }}
        </NuxtLink>
      </template>
    </TabStrip>

    <section class="mx-auto max-w-4xl px-4 py-6">
      <AsyncSection :error="errorMessage ?? undefined" :loading="loading">
        <div v-if="product" class="space-y-6">
          <ProductDetailHeader :product="product"/>
          <ProductDescription :markdown="product.description"/>
          <ProductDetailActions :product="product" :signed-in="signedIn" @download="wizardOpen = true"/>
        </div>
      </AsyncSection>
    </section>

    </main>

    <AppFooter/>

    <DownloadWizard v-if="wizardOpen && product" :product="product" @close="wizardOpen = false"/>
  </div>
</template>
