/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import type {ProductSummary} from '~/api/admin'

const {t} = useI18n()

defineProps<{ guildId: string, product: ProductSummary }>()
const emit = defineEmits<{ changed: [] }>()

/** Collapsed by default: a guild with a dozen products is a list, not a dozen forms. */
const open = ref(false)
</script>

<template>
  <li class="space-y-2 p-3 text-sm">
    <div class="flex items-center gap-2">
      <ProductIcon :icon-url="product.iconUrl" :name="product.name" size="sm"/>
      <span class="font-medium">{{ product.name }}</span>
      <NeutralBadge v-if="product.free">{{ t('page.admin.g.guildId.products.free') }}</NeutralBadge>
      <NeutralBadge v-else>{{ t('page.admin.g.guildId.products.premium') }}</NeutralBadge>
      <SecondaryButton class="ml-auto" compact @click="open = !open">
        {{ open ? t('ui.productAdminRow.done') : t('ui.productAdminRow.edit') }}
      </SecondaryButton>
    </div>
    <MutedText tag="div">
      id {{ product.id }} &middot; role {{ product.roleId }}{{ product.url ? ` · ${product.url}` : '' }}
    </MutedText>

    <div v-if="open" class="space-y-4 border-t border-border-light pt-3 dark:border-border-dark">
      <ProductIconUpload
          :guild-id="guildId"
          :icon-url="product.iconUrl"
          :product-id="product.id"
          :product-name="product.name"
          @changed="emit('changed')"
      />
      <ProductEditForm :guild-id="guildId" :product="product" @saved="emit('changed')"/>
    </div>
  </li>
</template>
