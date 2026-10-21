/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {KioskProductDetail} from '~/api/kiosk'

/**
 * The products around this one: what it belongs to and needs, its own add-ons, and the add-ons it
 * sits beside. Nothing at all for a product that has none of them.
 */
const {t} = useI18n()

defineProps<{ product: KioskProductDetail }>()
</script>

<template>
  <div v-if="product.parent || product.addons.length || product.similar.length" class="space-y-4" data-testid="product-family">
    <section v-if="product.parent" class="space-y-2">
      <SectionLabel>{{ t('common.addon.belongsTo') }}</SectionLabel>
      <ul><RelatedProductLink :product="product.parent"/></ul>
      <MutedText size="xs" tag="p">{{ t('common.addon.requires', {name: product.parent.name}) }}</MutedText>
    </section>
    <section v-if="product.addons.length" class="space-y-2">
      <SectionLabel>{{ t('common.addon.addons') }}</SectionLabel>
      <ul class="space-y-2">
        <RelatedProductLink v-for="addon in product.addons" :key="addon.id" :product="addon"/>
      </ul>
    </section>
    <section v-if="product.similar.length" class="space-y-2">
      <SectionLabel>{{ t('common.addon.similar') }}</SectionLabel>
      <ul class="space-y-2">
        <RelatedProductLink v-for="sibling in product.similar" :key="sibling.id" :product="sibling"/>
      </ul>
    </section>
  </div>
</template>
