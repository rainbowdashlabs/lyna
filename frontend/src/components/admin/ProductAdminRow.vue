/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {ProductSummary} from '~/api/admin'

/**
 * A product in the guild's list, leading to the page where everything about it is managed.
 */
const {t} = useI18n()

defineProps<{ guildId: string, product: ProductSummary }>()
</script>

<template>
  <li>
    <NuxtLink
        :to="`/admin/g/${guildId}/products/${product.id}`"
        class="flex items-center gap-2 p-3 text-sm hover:bg-primary/10"
    >
      <ProductIcon :icon-url="product.iconUrl" :name="product.name" size="sm"/>
      <span class="font-medium">{{ product.name }}</span>
      <NeutralBadge v-if="product.free">{{ t('page.admin.g.guildId.products.free') }}</NeutralBadge>
      <NeutralBadge v-else>{{ t('page.admin.g.guildId.products.premium') }}</NeutralBadge>
      <MutedText class="font-data ml-auto" tag="span">#{{ product.id }}</MutedText>
    </NuxtLink>
  </li>
</template>
