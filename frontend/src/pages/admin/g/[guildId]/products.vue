<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {createGuildProduct, listGuildProducts, type ProductSummary} from '~/api/admin'
import PrimaryButton from '~/components/button/PrimaryButton.vue'
import Spinner from '~/components/feedback/Spinner.vue'

const {t} = useI18n()

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
      <PageHeader>
        {{ t('page.admin.g.guildId.products.products') }}
      </PageHeader>
      <PrimaryButton @click="showCreate = !showCreate">
        {{ showCreate ? 'Cancel' : 'New product' }}
      </PrimaryButton>
    </header>

    <section
        v-if="showCreate"
        class="mb-4 rounded-theme border border-border-light dark:border-border-dark p-4"
    >
      <CardHeader>
        {{ t('page.admin.g.guildId.products.createProduct') }}
      </CardHeader>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <LabelledField :label="t('page.admin.g.guildId.products.name')">
          <TextInput v-model="createName" required/>
        </LabelledField>
        <LabelledField :label="t('page.admin.g.guildId.products.roleId')">
          <TextInput v-model="createRoleId" required/>
        </LabelledField>
        <LabelledField :label="t('page.admin.g.guildId.products.projectUrlOptional')">
          <TextInput v-model="createUrl" type="url"/>
        </LabelledField>
        <label class="flex items-center gap-2 text-sm">
          <CheckboxInput v-model="createFree"/> {{ t('page.admin.g.guildId.products.freeProduct') }}
        </label>
        <label class="flex items-center gap-2 text-sm">
          <CheckboxInput v-model="createTrial"/> {{ t('page.admin.g.guildId.products.trialAvailable') }}
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
      <li v-for="p in products" :key="p.id" class="space-y-2 p-3 text-sm">
        <div class="flex items-center gap-2">
          <span class="font-medium">{{ p.name }}</span>
          <SuccessBadge v-if="p.free">{{ t('page.admin.g.guildId.products.free') }}</SuccessBadge>
          <SecondaryBadge v-else>{{ t('page.admin.g.guildId.products.premium') }}</SecondaryBadge>
        </div>
        <MutedText tag="div">
          id {{ p.id }} &middot; role {{ p.roleId }}{{ p.url ? ` · ${p.url}` : '' }}
        </MutedText>
        <ProductIconField
            :guild-id="guildId"
            :icon-url="p.iconUrl"
            :product-id="p.id"
            :product-name="p.name"
            @saved="p.iconUrl = $event || null"
        />
      </li>
    </ul>
    <div v-else class="rounded-theme border border-border-light dark:border-border-dark p-8 text-center opacity-70">
      {{ t('page.admin.g.guildId.products.noProductsYet') }}
    </div>
  </div>
</template>
