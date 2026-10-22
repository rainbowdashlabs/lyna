/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {KioskProduct} from '~/api/kiosk'
import {productFamilies} from '~/util/productFamilies'
import type {StorefrontView} from '~/util/storefrontView'

/**
 * The storefront's products, as tiles or as a list that also says what each one is about. Each
 * add-on follows the product it belongs to; the list indents it beneath it.
 */
const props = defineProps<{
    products: KioskProduct[]
    /** Every product, shown or not, so an add-on can name a parent the filters hid. */
    catalogue: KioskProduct[]
    view: StorefrontView
    /** Whether anybody is signed in, which decides whether to offer the linking hint. */
    signedIn: boolean
}>()

const ordered = computed(() => productFamilies(props.products, product => product.id, product => product.parentId)
    .flatMap(family => [...family.heads, ...family.addons]))

const names = computed(() => new Map(props.catalogue.map(product => [product.id, product.name])))

function parentName(product: KioskProduct): string | null {
  return product.parentId == null ? null : names.value.get(product.parentId) ?? null
}

defineEmits<{
    download: [product: KioskProduct]
}>()
</script>

<template>
  <ul
      v-if="view === 'list'"
      class="divide-y divide-border-light rounded-theme border border-border-light dark:divide-border-dark dark:border-border-dark"
  >
    <ProductRow
        v-for="product in ordered"
        :key="product.id"
        :parent-name="parentName(product)"
        :product="product"
        :signed-in="signedIn"
        @download="$emit('download', $event)"
    />
  </ul>
  <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
    <ProductTile
        v-for="product in ordered"
        :key="product.id"
        :parent-name="parentName(product)"
        :product="product"
        :signed-in="signedIn"
        @download="$emit('download', $event)"
    />
  </div>
</template>
