/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {ProductSummary} from '~/api/admin'
import type {ProductAdminTab} from './productAdminTabs'

/**
 * What the current tab of a product's admin page shows.
 */
defineProps<{ guildId: string, product: ProductSummary, tab: ProductAdminTab }>()
defineEmits<{ changed: [], deleted: [] }>()
</script>

<template>
  <div v-if="tab === 'general'" class="space-y-6">
    <ProductIconUpload
        :guild-id="guildId"
        :icon-url="product.iconUrl"
        :product-id="product.id"
        :product-name="product.name"
        @changed="$emit('changed')"
    />
    <ProductEditForm :key="product.id" :guild-id="guildId" :product="product" @saved="$emit('changed')"/>
    <ProductDeleteZone :guild-id="guildId" :product="product" @deleted="$emit('deleted')"/>
  </div>
  <ProductPageEditor v-else-if="tab === 'page'" :guild-id="guildId" :product="product" @saved="$emit('changed')"/>
  <ReleaseWebhookPanel v-else :guild-id="guildId" :product-id="product.id"/>
</template>
