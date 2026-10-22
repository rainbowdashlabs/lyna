/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {RelatedProduct} from '~/api/kiosk'

/**
 * Another product, named on this one's page: its icon, a link to its page, and whether the visitor
 * has it already.
 */
const {t} = useI18n()

defineProps<{ product: RelatedProduct }>()
</script>

<template>
  <li class="flex items-center gap-2">
    <ProductIcon :icon-url="product.iconUrl" :name="product.name" size="sm"/>
    <NuxtLink :to="`/products/${product.id}`" class="min-w-0 flex-1 break-words text-sm hover:underline">{{ product.name }}</NuxtLink>
    <NeutralBadge v-if="product.free">{{ t('ui.productTile.free') }}</NeutralBadge>
    <SecondaryBadge v-else-if="product.entitled">{{ t('ui.productTile.owned') }}</SecondaryBadge>
    <NeutralBadge v-else>{{ t('ui.productTile.premium') }}</NeutralBadge>
  </li>
</template>
