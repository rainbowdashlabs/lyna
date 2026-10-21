/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {KioskProduct} from '~/api/kiosk'
import {formatDate} from '~/util/format'

const {t} = useI18n()

defineProps<{
    product: KioskProduct
    /** Whether anybody is signed in, which decides whether to offer the linking hint. */
    signedIn: boolean
    /** The product this one is an add-on of, or null when it stands on its own. */
    parentName?: string | null
}>()

defineEmits<{
    download: [product: KioskProduct]
}>()
</script>

<template>
  <article
      class="flex h-full flex-col rounded-theme border border-border-light bg-bg-light p-4 transition-shadow hover:shadow-lg dark:border-border-dark dark:bg-bg-dark"
  >
    <header class="flex items-start gap-3">
      <ProductIcon :icon-url="product.iconUrl" :name="product.name"/>
      <div class="min-w-0 flex-1">
        <NuxtLink :to="`/products/${product.id}`" class="block break-words hover:underline">
          <SectionHeader class="break-words">{{ product.name }}</SectionHeader>
        </NuxtLink>
        <NeutralBadge v-if="product.free">{{ t('ui.productTile.free') }}</NeutralBadge>
        <SecondaryBadge v-else-if="product.entitled">{{ t('ui.productTile.owned') }}</SecondaryBadge>
        <NeutralBadge v-else>{{ t('ui.productTile.premium') }}</NeutralBadge>
        <NeutralBadge v-if="product.parentId != null">{{ t('common.addon.addon') }}</NeutralBadge>
      </div>
    </header>

    <MutedText v-if="parentName" class="mt-3" size="xs" tag="p">{{ t('common.addon.requires', {name: parentName}) }}</MutedText>
    <MutedText v-if="product.latestVersion" class="font-data mt-3" size="xs" tag="p">
      {{ t('ui.productTile.latest', {version: product.latestVersion, date: formatDate(product.updatedAt)}) }}
    </MutedText>

    <ProductCallToAction :product="product" :signed-in="signedIn" class="mt-4" @download="$emit('download', product)"/>
  </article>
</template>
