/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import type {ProductOption} from '~/api/account'
import {productFamilies} from '~/util/productFamilies'

const {t} = useI18n()

const from = defineModel<string>('from', {required: true})
const to = defineModel<string>('to', {required: true})
const product = defineModel<number | null>('product', {required: true})
const source = defineModel<string | null>('source', {required: true})

const props = defineProps<{
    products: ProductOption[]
}>()

/** Each add-on beneath the product it belongs to, under that product's name when it is not an option itself. */
const families = computed(() => productFamilies(props.products, option => option.id, option => option.parentId))

/** The ways a download is recorded, which is what `source` can hold. */
const SOURCES = ['license', 'sub_license', 'trial', 'free', 'updater']
</script>

<template>
  <div class="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
    <LabelledField :label="t('ui.downloadFilters.from')">
      <DateInput v-model="from"/>
    </LabelledField>
    <LabelledField :label="t('ui.downloadFilters.to')">
      <DateInput v-model="to"/>
    </LabelledField>
    <LabelledField :label="t('common.product')">
      <SelectInput v-model="product">
        <option :value="null">{{ t('ui.downloadFilters.any') }}</option>
        <template v-for="family in families" :key="family.productId">
          <option v-for="option in family.heads" :key="option.id" :value="option.id">{{ option.name }}</option>
          <optgroup v-if="family.addons.length" :label="family.heads[0]?.name ?? family.addons[0]?.parentName ?? ''">
            <option v-for="option in family.addons" :key="option.id" :value="option.id">{{ option.name }}</option>
          </optgroup>
        </template>
      </SelectInput>
    </LabelledField>
    <LabelledField :label="t('ui.downloadFilters.source')">
      <SelectInput v-model="source">
        <option :value="null">{{ t('ui.downloadFilters.any') }}</option>
        <option v-for="name in SOURCES" :key="name" :value="name">{{ name }}</option>
      </SelectInput>
    </LabelledField>
  </div>
</template>
