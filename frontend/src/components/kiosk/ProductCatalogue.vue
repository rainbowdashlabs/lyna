/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {KioskProduct} from '~/api/kiosk'
import type {StorefrontView} from '~/util/storefrontView'

/**
 * The storefront's products, as tiles or as a list that also says what each one is about.
 */
defineProps<{
    products: KioskProduct[]
    view: StorefrontView
    /** Whether anybody is signed in, which decides whether to offer the linking hint. */
    signedIn: boolean
}>()

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
        v-for="product in products"
        :key="product.id"
        :product="product"
        :signed-in="signedIn"
        @download="$emit('download', $event)"
    />
  </ul>
  <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
    <ProductTile
        v-for="product in products"
        :key="product.id"
        :product="product"
        :signed-in="signedIn"
        @download="$emit('download', $event)"
    />
  </div>
</template>
