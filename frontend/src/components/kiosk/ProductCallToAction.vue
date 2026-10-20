/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import {callToAction, type KioskProduct} from '~/api/kiosk'

/**
 * What a product offers this visitor - the project page, and download, buy or a note that it is not
 * sold here - the same on a tile and in a row of the list.
 */
const props = defineProps<{
    product: KioskProduct
    /** Whether anybody is signed in, which decides whether to offer the linking hint. */
    signedIn: boolean
}>()

const emit = defineEmits<{
    download: [product: KioskProduct]
}>()

const {t} = useI18n()
const action = computed(() => callToAction(props.product))
</script>

<template>
  <div>
    <footer class="flex items-center justify-end gap-2">
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
      <PrimaryButton v-if="action === 'download'" @click="emit('download', product)">
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
      <NuxtLink class="text-primary hover:underline" to="/account/security">{{ t('ui.productTile.linkYourAccount') }}</NuxtLink>
    </MutedText>
  </div>
</template>
