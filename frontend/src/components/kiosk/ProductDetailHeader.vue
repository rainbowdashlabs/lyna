/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {KioskProductDetail} from '~/api/kiosk'

const {t} = useI18n()

defineProps<{ product: KioskProductDetail }>()
</script>

<template>
  <header class="flex items-start gap-4">
    <ProductIcon :icon-url="product.iconUrl" :name="product.name"/>
    <div class="min-w-0 flex-1">
      <PageHeader>{{ product.name }}</PageHeader>
      <div class="mt-1 flex items-center gap-2">
        <NeutralBadge v-if="product.free">{{ t('ui.productTile.free') }}</NeutralBadge>
        <SecondaryBadge v-else-if="product.entitled">{{ t('ui.productTile.owned') }}</SecondaryBadge>
        <NeutralBadge v-else>{{ t('ui.productTile.premium') }}</NeutralBadge>
        <NeutralBadge v-if="product.parent">{{ t('common.addon.addon') }}</NeutralBadge>
        <a
            v-if="product.url"
            :href="product.url"
            class="text-sm text-(--text-muted) hover:text-(--text)"
            rel="noopener noreferrer"
            target="_blank"
        >
          <font-awesome-icon :icon="['fab', 'github']"/>
          <span class="ml-1">{{ t('page.products.id.projectPage') }}</span>
        </a>
        <a
            v-if="product.wikiUrl"
            :href="product.wikiUrl"
            class="text-sm text-(--text-muted) hover:text-(--text)"
            rel="noopener noreferrer"
            target="_blank"
        >
          <font-awesome-icon :icon="['fas', 'book']"/>
          <span class="ml-1">{{ t('page.products.id.wiki') }}</span>
        </a>
      </div>
    </div>
    <ProductEditLink :product="product"/>
  </header>
</template>
