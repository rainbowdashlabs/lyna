/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {ProductOption} from '~/api/account'

const from = defineModel<string>('from', {required: true})
const to = defineModel<string>('to', {required: true})
const product = defineModel<number | null>('product', {required: true})
const source = defineModel<string | null>('source', {required: true})

defineProps<{
    products: ProductOption[]
}>()

/** The entitlement paths the proxy records, which is what `source` can hold. */
const SOURCES = ['license', 'sub_license', 'trial', 'free']
</script>

<template>
  <div class="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
    <LabelledField label="From">
      <DateInput v-model="from"/>
    </LabelledField>
    <LabelledField label="To">
      <DateInput v-model="to"/>
    </LabelledField>
    <LabelledField label="Product">
      <SelectInput v-model="product">
        <option :value="null">Any</option>
        <option v-for="option in products" :key="option.id" :value="option.id">{{ option.name }}</option>
      </SelectInput>
    </LabelledField>
    <LabelledField label="Source">
      <SelectInput v-model="source">
        <option :value="null">Any</option>
        <option v-for="name in SOURCES" :key="name" :value="name">{{ name }}</option>
      </SelectInput>
    </LabelledField>
  </div>
</template>
