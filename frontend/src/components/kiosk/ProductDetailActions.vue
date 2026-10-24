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
      <BuyButton v-else-if="product.purchaseOptions.length" :options="product.purchaseOptions"/>
      <MutedText v-else tag="p">{{ t('ui.productTile.notForSaleHere') }}</MutedText>
    </div>
    <EmptyHint v-if="!product.free && !product.entitled && signedIn">
      {{ t('ui.productTile.alreadyBoughtIt') }}
      <NuxtLink class="text-primary hover:underline" to="/account/security">
        {{ t('ui.productTile.linkYourAccount') }}
      </NuxtLink>
    </EmptyHint>
  </div>
</template>
