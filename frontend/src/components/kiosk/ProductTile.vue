/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import {callToAction, type KioskProduct} from '~/api/kiosk'

const {t} = useI18n()

const props = defineProps<{
    product: KioskProduct
    /** Whether anybody is signed in, which decides whether to offer the linking hint. */
    signedIn: boolean
}>()

defineEmits<{
    download: [product: KioskProduct]
}>()

const action = computed(() => callToAction(props.product))
</script>

<template>
  <article
      class="flex h-full flex-col rounded-theme border border-border-light bg-bg-light p-4 transition-shadow hover:shadow-lg dark:border-border-dark dark:bg-bg-dark"
  >
    <header class="flex items-start gap-3">
      <ProductIcon :icon-url="product.iconUrl" :name="product.name"/>
      <div class="min-w-0 flex-1">
        <SectionHeader class="truncate">{{ product.name }}</SectionHeader>
        <NeutralBadge v-if="product.free">{{ t('ui.productTile.free') }}</NeutralBadge>
        <SecondaryBadge v-else-if="product.entitled">{{ t('ui.productTile.owned') }}</SecondaryBadge>
        <NeutralBadge v-else>{{ t('ui.productTile.premium') }}</NeutralBadge>
      </div>
    </header>

    <footer class="mt-4 flex items-center justify-end gap-2">
      <a
          v-if="product.url"
          :href="product.url"
          :title="`Open the project page for ${product.name}`"
          class="inline-flex items-center justify-center rounded-theme p-2 text-(--text-muted) transition-colors hover:text-(--text)"
          rel="noopener noreferrer"
          target="_blank"
      >
        <font-awesome-icon :icon="['fab', 'github']"/>
      </a>
      <PrimaryButton v-if="action === 'download'" @click="$emit('download', product)">
        <font-awesome-icon :icon="['fas', 'download']" class="mr-1"/>
        {{ t('common.download') }}
      </PrimaryButton>
      <a
          v-else-if="action === 'buy'"
          :href="product.purchaseUrl!"
          class="rounded-theme inline-flex items-center border border-(--border) px-3 py-1.5 text-sm font-medium whitespace-nowrap transition-colors hover:border-secondary hover:bg-secondary/12"
          rel="noopener noreferrer"
          target="_blank"
      >
        <font-awesome-icon :icon="['fas', 'cart-shopping']" class="mr-1"/>
        {{ t('ui.productTile.buyOnKoFi') }}
      </a>
      <MutedText v-else size="sm">{{ t('ui.productTile.notForSaleHere') }}</MutedText>
    </footer>
    <MutedText v-if="action === 'buy' && signedIn" class="mt-2 block text-right" size="xs">
      {{ t('ui.productTile.alreadyBoughtIt') }}
      <NuxtLink class="text-primary hover:underline" to="/account/security">{{ t('ui.productTile.linkYourDiscord') }}</NuxtLink>
    </MutedText>
  </article>
</template>
