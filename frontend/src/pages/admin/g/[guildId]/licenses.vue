<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {createGuildLicense, listGuildLicenses, listGuildProducts, type LicenseSummary, type ProductSummary} from '~/api/admin'
import PrimaryButton from '~/components/button/PrimaryButton.vue'
import Spinner from '~/components/feedback/Spinner.vue'

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))

const licenses = ref<LicenseSummary[]>([])
const products = ref<ProductSummary[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)

const showCreate = ref(false)
const createProductId = ref<number | null>(null)
const createIdentifier = ref('')
const createBusy = ref(false)
const createError = ref<string | null>(null)
const issuedKey = ref<string | null>(null)

async function load() {
  loading.value = true
  errorMessage.value = null
  try {
    [products.value, licenses.value] = await Promise.all([
      listGuildProducts(guildId.value),
      listGuildLicenses(guildId.value),
    ])
  } catch (e) {
    errorMessage.value = (e as Error).message ?? 'Failed to load licenses'
  } finally {
    loading.value = false
  }
}

watch(() => route.params.guildId, (next) => {
  guildId.value = String(next)
  load()
})

onMounted(load)

async function submitCreate() {
  if (createProductId.value == null || !createIdentifier.value) {
    createError.value = 'Product and identifier are required.'
    return
  }
  createBusy.value = true
  createError.value = null
  try {
    const detail = await createGuildLicense(guildId.value, createProductId.value, createIdentifier.value)
    issuedKey.value = detail.key
    createProductId.value = null
    createIdentifier.value = ''
    await load()
  } catch (e) {
    const error = e as {response?: {data?: string}}
    createError.value = typeof error.response?.data === 'string' ? error.response.data : 'Failed to create license.'
  } finally {
    createBusy.value = false
  }
}
</script>

<template>
  <div>
    <header class="mb-4 flex items-center justify-between">
      <PageHeader>
        Licenses
      </PageHeader>
      <PrimaryButton @click="showCreate = !showCreate; issuedKey = null">
        {{ showCreate ? 'Cancel' : 'Issue license' }}
      </PrimaryButton>
    </header>

    <section
        v-if="showCreate"
        class="mb-4 rounded-theme border border-border-light dark:border-border-dark p-4"
    >
      <CardHeader>
        Issue license
      </CardHeader>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label class="block text-sm">
          <span>Product</span>
          <select
              v-model.number="createProductId"
              class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
          >
            <option :value="null" disabled>
              Choose…
            </option>
            <option v-for="p in products" :key="p.id" :value="p.id">
              {{ p.name }}
            </option>
          </select>
        </label>
        <label class="block text-sm">
          <span>Identifier (mail / order id)</span>
          <input
              v-model="createIdentifier"
              required
              class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
          />
        </label>
      </div>
      <div v-if="createError" class="mt-2 text-sm text-error">
        {{ createError }}
      </div>
      <div class="mt-3">
        <PrimaryButton :disabled="createBusy" @click="submitCreate">
          {{ createBusy ? 'Issuing…' : 'Issue' }}
        </PrimaryButton>
      </div>
      <div
          v-if="issuedKey"
          class="mt-4 rounded-theme border border-success/40 bg-success/10 p-3 text-sm"
      >
        <div class="font-semibold">
          License issued
        </div>
        <code class="mt-1 block break-all">{{ issuedKey }}</code>
      </div>
    </section>

    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <div v-else-if="errorMessage" class="rounded-theme border border-error/40 bg-error/10 p-4 text-error">
      {{ errorMessage }}
    </div>
    <ul v-else-if="licenses.length" class="divide-y divide-border-light dark:divide-border-dark rounded-theme border border-border-light dark:border-border-dark">
      <li v-for="l in licenses" :key="l.id" class="flex items-center justify-between p-3 text-sm">
        <div>
          <div class="font-medium">
            {{ l.productName }}
          </div>
          <div class="text-xs opacity-60">
            id {{ l.id }} · {{ l.identifier }} · owner {{ l.owner || 'unclaimed' }} · {{ l.shareeCount }} sharees
          </div>
        </div>
      </li>
    </ul>
    <div v-else class="rounded-theme border border-border-light dark:border-border-dark p-8 text-center opacity-70">
      No licenses yet.
    </div>
  </div>
</template>
