/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {KioskProduct} from '~/api/kiosk'
import {formatDate} from '~/util/format'

/**
 * A product as one row of the storefront's list: what a tile shows, and the first lines of what it
 * says about itself, which a grid of tiles has no room for.
 */
defineProps<{
    product: KioskProduct
    /** Whether anybody is signed in, which decides whether to offer the linking hint. */
    signedIn: boolean
}>()

defineEmits<{
    download: [product: KioskProduct]
}>()

const {t} = useI18n()
</script>

<template>
  <li class="flex flex-col gap-3 p-4 sm:flex-row sm:items-start">
    <ProductIcon :icon-url="product.iconUrl" :name="product.name"/>
    <div class="min-w-0 flex-1 space-y-1">
      <div class="flex flex-wrap items-center gap-2">
        <NuxtLink :to="`/products/${product.id}`" class="break-words hover:underline">
          <SectionHeader class="break-words">{{ product.name }}</SectionHeader>
        </NuxtLink>
        <NeutralBadge v-if="product.free">{{ t('ui.productTile.free') }}</NeutralBadge>
        <SecondaryBadge v-else-if="product.entitled">{{ t('ui.productTile.owned') }}</SecondaryBadge>
        <NeutralBadge v-else>{{ t('ui.productTile.premium') }}</NeutralBadge>
      </div>
      <MutedText v-if="product.summary" size="sm" tag="p">{{ product.summary }}</MutedText>
      <MutedText v-if="product.latestVersion" class="font-data" size="xs" tag="p">
        {{ t('ui.productTile.latest', {version: product.latestVersion, date: formatDate(product.updatedAt)}) }}
      </MutedText>
    </div>
    <ProductCallToAction :product="product" :signed-in="signedIn" class="sm:w-56 sm:shrink-0" @download="$emit('download', product)"/>
  </li>
</template>
