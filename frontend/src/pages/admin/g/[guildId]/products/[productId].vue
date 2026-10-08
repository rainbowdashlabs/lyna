/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {listGuildProducts, type ProductSummary} from '~/api/admin'
import {PRODUCT_ADMIN_TABS, type ProductAdminTab} from '~/components/admin/productAdminTabs'

/**
 * Everything about one product, a tab per concern. The tab is kept in the address, so a link can
 * open the one it means and reloading stays on it.
 */
definePageMeta({layout: 'admin'})

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const guildId = String(route.params.guildId)
const productId = Number(route.params.productId)

const product = ref<ProductSummary | null>(null)
const loading = ref(true)
const errorMessage = ref('')

const tab = computed<ProductAdminTab>({
  get: () => PRODUCT_ADMIN_TABS.includes(route.query.tab as ProductAdminTab) ? route.query.tab as ProductAdminTab : 'general',
  set: next => router.replace({query: {...route.query, tab: next}}),
})

async function load() {
  try {
    product.value = (await listGuildProducts(guildId)).find(candidate => candidate.id === productId) ?? null
    if (!product.value) errorMessage.value = t('page.admin.g.guildId.products.productId.notFound')
  } catch (e) {
    errorMessage.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

async function deleted() {
  await router.replace(`/admin/g/${guildId}/products`)
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <NuxtLink :to="`/admin/g/${guildId}/products`" class="font-data text-xs text-(--text-muted) hover:text-(--text)">
      ← {{ t('page.admin.g.guildId.products.products') }}
    </NuxtLink>
    <AsyncSection :error="errorMessage" :loading="loading">
      <template v-if="product">
        <PageHeader>{{ product.name }}</PageHeader>
        <nav class="flex overflow-x-auto border-b border-border-light bg-bg-light-accent dark:border-border-dark dark:bg-bg-dark-accent" role="tablist">
          <TabButton v-for="name in PRODUCT_ADMIN_TABS" :key="name" :active="tab === name" @select="tab = name">
            {{ t(`page.admin.g.guildId.products.productId.tabs.${name}`) }}
          </TabButton>
        </nav>
        <ProductAdminBody :guild-id="guildId" :product="product" :tab="tab" @changed="load" @deleted="deleted"/>
      </template>
    </AsyncSection>
  </div>
</template>
