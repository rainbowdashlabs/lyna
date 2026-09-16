<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {type KioskProduct, listProducts} from '~/api/kiosk'
import {useSession} from '~/composables/useSession'

const {t} = useI18n()

const {account, hydrate} = useSession()

const products = ref<KioskProduct[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const wizardProduct = ref<KioskProduct | null>(null)
const search = ref('')
const filter = ref('all')

const signedIn = computed(() => account.value !== null)

/**
 * Resolves who is looking before drawing the catalogue.
 *
 * <p>The session decides which chips the filter row offers and whether a premium tile suggests
 * linking Discord, so it is settled first; the catalogue call carries the token either way, which
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

onMounted(load)

const shown = computed(() => {
  const term = search.value.trim().toLowerCase()
  return products.value.filter(product => {
    if (term && !product.name.toLowerCase().includes(term)) return false
    if (filter.value === 'free') return product.free
    if (filter.value === 'owned') return product.entitled
    return true
  })
})

function clearFilters() {
  search.value = ''
  filter.value = 'all'
}
</script>

<template>
  <main class="min-h-screen pb-12">
    <header class="border-b border-border-light bg-primary py-6 text-primary-text dark:border-border-dark">
      <div class="mx-auto max-w-6xl px-4">
        <PageHeader>
          {{ t('kiosk.title') }}
        </PageHeader>
        <p class="opacity-80">
          {{ t('page.home.browseAndDownloadPluginReleases') }}
        </p>
      </div>
    </header>

    <section class="mx-auto max-w-6xl px-4 py-6">
      <KioskFilters v-model:filter="filter" v-model:search="search" :signed-in="signedIn"/>
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
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          <ProductTile
              v-for="product in shown"
              :key="product.id"
              :product="product"
              :signed-in="signedIn"
              @download="wizardProduct = $event"
          />
        </div>
      </AsyncSection>
    </section>

    <DownloadWizard
        v-if="wizardProduct"
        :product="wizardProduct"
        @close="wizardProduct = null"
    />
  </main>
</template>
