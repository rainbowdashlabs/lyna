<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {listProducts, type KioskProduct} from '~/api/kiosk'
import ProductTile from '~/components/kiosk/ProductTile.vue'
import DownloadWizard from '~/components/kiosk/DownloadWizard.vue'
import Spinner from '~/components/feedback/Spinner.vue'

const products = ref<KioskProduct[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const wizardProduct = ref<KioskProduct | null>(null)
const search = ref('')

onMounted(async () => {
  try {
    products.value = await listProducts()
  } catch (e) {
    error.value = (e as Error).message ?? 'Failed to load products'
  } finally {
    loading.value = false
  }
})

function filtered(): KioskProduct[] {
  const q = search.value.trim().toLowerCase()
  if (!q) return products.value
  return products.value.filter(p => p.name.toLowerCase().includes(q))
}
</script>

<template>
  <main class="min-h-screen pb-12">
    <header class="border-b border-border-light dark:border-border-dark bg-primary py-6 text-primary-text">
      <div class="mx-auto max-w-6xl px-4">
        <h1 class="text-2xl font-bold tracking-tight">
          Lyna Download Center
        </h1>
        <p class="opacity-80">
          Browse and download plugin releases.
        </p>
      </div>
    </header>

    <section class="mx-auto max-w-6xl px-4 py-6">
      <div class="mb-4">
        <input
            v-model="search"
            type="search"
            placeholder="Search plugins…"
            class="w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2 outline-none focus:border-primary"
        />
      </div>

      <div v-if="loading" class="flex justify-center py-12">
        <Spinner size="lg" />
      </div>

      <div
          v-else-if="error"
          class="rounded-theme border border-error/30 bg-error/10 p-4 text-error"
      >
        {{ error }}
      </div>

      <div
          v-else-if="filtered().length === 0"
          class="rounded-theme border border-border-light dark:border-border-dark p-8 text-center opacity-70"
      >
        <p>No plugins match your search.</p>
      </div>

      <div
          v-else
          class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
      >
        <ProductTile
            v-for="product in filtered()"
            :key="product.id"
            :product="product"
            @download="(p) => wizardProduct = p"
        />
      </div>
    </section>

    <DownloadWizard :product="wizardProduct" @close="wizardProduct = null" />
  </main>
</template>
