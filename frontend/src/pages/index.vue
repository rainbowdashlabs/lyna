/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref, watch} from 'vue'
import {type KioskProduct, listProducts} from '~/api/kiosk'
import {useSession} from '~/composables/useSession'
import {useLinkPreview} from '~/composables/useLinkPreview'
import {useServerFetch} from '~/composables/useServerFetch'
import {rememberedView, rememberView, sortProducts, type StorefrontSort, type StorefrontView} from '~/util/storefrontView'

const {t} = useI18n()

const {account, hydrate} = useSession()

const {data: catalogue} = useServerFetch<KioskProduct[]>('preview-catalogue', '/api/v1/products')
useLinkPreview(computed(() => {
  const count = catalogue.value?.length ?? 0
  const description = t('preview.storefront.description', {count})
  return {
    title: t('kiosk.title'),
    description,
    image: null,
    card: {heading: `## ${t('kiosk.title')}`, body: description, thumbnail: null, buttons: [{label: t('preview.storefront.browse'), url: '/'}]},
  }
}))

const products = ref<KioskProduct[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const wizardProduct = ref<KioskProduct | null>(null)
const search = ref('')
const filter = ref('all')
const view = ref<StorefrontView>('tiles')
const sort = ref<StorefrontSort>('name')
watch(view, rememberView)

const signedIn = computed(() => account.value !== null)

/**
 * Resolves who is looking before drawing the catalogue.
 *
 * <p>The session decides which chips the filter row offers and whether a premium tile suggests
 * linking Discord, so it is settled first; the catalogue call carries the session cookie either way, which
 * is what makes a tile say `Owned`.
 */
async function load() {
  try {
    await hydrate()
    products.value = await listProducts()
  } catch (e) {
    errorMessage.value = (e as Error).message ?? 'Failed to load products'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  view.value = rememberedView()
  void load()
})

const shown = computed(() => {
  const term = search.value.trim().toLowerCase()
  return sortProducts(products.value.filter(product => {
    if (term && !product.name.toLowerCase().includes(term)) return false
    if (filter.value === 'free') return product.free
    if (filter.value === 'owned') return product.entitled
    return true
  }), sort.value)
})

function clearFilters() {
  search.value = ''
  filter.value = 'all'
}
</script>

<template>
  <div class="flex flex-1 flex-col">
    <main class="flex-1 pb-12">
    <TabStrip :tabs="[{to: '/', label: t('kiosk.title'), exact: true}]">
      <template #end>
        <AdminLink/>
        <NuxtLink
            :to="signedIn ? '/account' : '/login'"
            class="font-data px-4 py-2.5 text-xs whitespace-nowrap text-(--text-muted) hover:text-(--text)"
        >
          {{ signedIn ? t('kiosk.yourAccount') : t('kiosk.signIn') }}
        </NuxtLink>
      </template>
    </TabStrip>

    <header class="mx-auto max-w-6xl px-4 pt-6">
      <PageHeader>{{ t('kiosk.title') }}</PageHeader>
      <MutedText tag="p">{{ t('page.home.browseAndDownloadPluginReleases') }}</MutedText>
    </header>

    <section class="mx-auto max-w-6xl px-4 py-6">
      <KioskFilters v-model:filter="filter" v-model:search="search" v-model:sort="sort" v-model:view="view" :loading="loading" :signed-in="signedIn"/>
      <AsyncSection
          :empty="!loading && shown.length === 0"
          :error="errorMessage ?? undefined"
          :loading="loading"
      >
        <template #empty>
          <EmptyState>
            <p class="mb-3">{{ t('page.home.noPluginsMatch') }}</p>
            <SecondaryButton compact @click="clearFilters">{{ t('page.home.clearFilters') }}</SecondaryButton>
          </EmptyState>
        </template>
        <ProductCatalogue :catalogue="products" :products="shown" :signed-in="signedIn" :view="view" @download="wizardProduct = $event"/>
      </AsyncSection>
    </section>

    </main>

    <DownloadWizard
        v-if="wizardProduct"
        :product="wizardProduct"
        @close="wizardProduct = null"
    />
  </div>
</template>
