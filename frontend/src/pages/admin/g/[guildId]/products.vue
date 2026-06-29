<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {createGuildProduct, listGuildProducts, type ProductSummary} from '~/api/admin'
import PrimaryButton from '~/components/button/PrimaryButton.vue'
import Spinner from '~/components/feedback/Spinner.vue'

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))

const products = ref<ProductSummary[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)

const showCreate = ref(false)
const createName = ref('')
const createUrl = ref('')
const createRoleId = ref('')
const createFree = ref(false)
const createTrial = ref(false)
const createBusy = ref(false)
const createError = ref<string | null>(null)

async function load() {
  loading.value = true
  errorMessage.value = null
  try {
    products.value = await listGuildProducts(guildId.value)
  } catch (e) {
    errorMessage.value = (e as Error).message ?? 'Failed to load products'
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
  if (!createName.value || !createRoleId.value) {
    createError.value = 'Name and role id are required.'
    return
  }
  createBusy.value = true
  createError.value = null
  try {
    await createGuildProduct(guildId.value, {
      name: createName.value,
      url: createUrl.value || null,
      roleId: createRoleId.value,
      free: createFree.value,
      trial: createTrial.value,
    })
    showCreate.value = false
    createName.value = ''
    createUrl.value = ''
    createRoleId.value = ''
    createFree.value = false
    createTrial.value = false
    await load()
  } catch (e) {
    const error = e as {response?: {data?: string}}
    createError.value = typeof error.response?.data === 'string' ? error.response.data : 'Failed to create product.'
  } finally {
    createBusy.value = false
  }
}
</script>

<template>
  <div>
    <header class="mb-4 flex items-center justify-between">
      <h1 class="text-2xl font-bold">
        Products
      </h1>
      <PrimaryButton @click="showCreate = !showCreate">
        {{ showCreate ? 'Cancel' : 'New product' }}
      </PrimaryButton>
    </header>

    <section
        v-if="showCreate"
        class="mb-4 rounded-theme border border-border-light dark:border-border-dark p-4"
    >
      <h2 class="mb-3 text-sm font-semibold uppercase tracking-wider opacity-70">
        Create product
      </h2>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label class="block text-sm">
          <span>Name</span>
          <input
              v-model="createName"
              required
              class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
          />
        </label>
        <label class="block text-sm">
          <span>Role id</span>
          <input
              v-model="createRoleId"
              inputmode="numeric"
              required
              class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
          />
        </label>
        <label class="block text-sm sm:col-span-2">
          <span>Project URL (optional)</span>
          <input
              v-model="createUrl"
              type="url"
              class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2"
          />
        </label>
        <label class="flex items-center gap-2 text-sm">
          <input v-model="createFree" type="checkbox" /> Free product
        </label>
        <label class="flex items-center gap-2 text-sm">
          <input v-model="createTrial" type="checkbox" /> Trial available
        </label>
      </div>
      <div v-if="createError" class="mt-2 text-sm text-error">
        {{ createError }}
      </div>
      <div class="mt-3">
        <PrimaryButton :disabled="createBusy" @click="submitCreate">
          {{ createBusy ? 'Creating…' : 'Create' }}
        </PrimaryButton>
      </div>
    </section>

    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <div v-else-if="errorMessage" class="rounded-theme border border-error/40 bg-error/10 p-4 text-error">
      {{ errorMessage }}
    </div>
    <ul v-else-if="products.length" class="divide-y divide-border-light dark:divide-border-dark rounded-theme border border-border-light dark:border-border-dark">
      <li v-for="p in products" :key="p.id" class="flex items-center justify-between p-3 text-sm">
        <div>
          <div class="font-medium">
            {{ p.name }}
          </div>
          <div class="text-xs opacity-60">
            id {{ p.id }} · role {{ p.roleId }}{{ p.url ? ` · ${p.url}` : '' }}
          </div>
        </div>
      </li>
    </ul>
    <div v-else class="rounded-theme border border-border-light dark:border-border-dark p-8 text-center opacity-70">
      No products yet.
    </div>
  </div>
</template>
