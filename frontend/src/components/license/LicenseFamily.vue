/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {LicenseView} from '~/api/account'
import type {RelatedProduct} from '~/api/kiosk'

/**
 * The products around the licence's product: the one it is an add-on of, and its own add-ons, each
 * saying whether this account has it already. Nothing for a product that has neither.
 */
const {t} = useI18n()

defineProps<{
    license: LicenseView
    addons: RelatedProduct[]
}>()
</script>

<template>
  <div v-if="license.parentId != null || addons.length" class="space-y-4" data-testid="license-family">
    <section v-if="license.parentId != null" class="space-y-2">
      <SectionLabel>{{ t('common.addon.belongsTo') }}</SectionLabel>
      <NuxtLink :to="`/products/${license.parentId}`" class="block text-sm hover:underline">{{ license.parentName }}</NuxtLink>
      <MutedText size="xs" tag="p">{{ t('common.addon.requires', {name: license.parentName}) }}</MutedText>
    </section>
    <section v-if="addons.length" class="space-y-2">
      <SectionLabel>{{ t('common.addon.addons') }}</SectionLabel>
      <ul class="space-y-2">
        <RelatedProductLink v-for="addon in addons" :key="addon.id" :product="addon"/>
      </ul>
    </section>
  </div>
</template>
