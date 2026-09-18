/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {KioskProductDetail} from '~/api/kiosk'

const {t} = useI18n()

defineProps<{ product: KioskProductDetail, signedIn: boolean }>()
defineEmits<{ download: [] }>()
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center gap-3">
      <PrimaryButton v-if="product.free || product.entitled" @click="$emit('download')">
        <font-awesome-icon :icon="['fas', 'download']" class="mr-1"/>
        {{ t('common.download') }}
      </PrimaryButton>
      <a
          v-else-if="product.purchaseUrl"
          :href="product.purchaseUrl"
          class="rounded-theme inline-flex items-center border border-(--border) px-3 py-1.5 text-sm font-medium whitespace-nowrap transition-colors hover:border-secondary hover:bg-secondary/12"
          rel="noopener noreferrer"
          target="_blank"
      >
        <font-awesome-icon :icon="['fas', 'cart-shopping']" class="mr-1"/>
        {{ t('ui.productTile.buyOnKoFi') }}
      </a>
      <MutedText v-else tag="p">{{ t('ui.productTile.notForSaleHere') }}</MutedText>
    </div>
    <EmptyHint v-if="!product.free && !product.entitled && signedIn">
      {{ t('ui.productTile.alreadyBoughtIt') }}
      <NuxtLink class="text-primary hover:underline" to="/account/security">
        {{ t('ui.productTile.linkYourDiscord') }}
      </NuxtLink>
    </EmptyHint>
  </div>
</template>
