/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {RoleAccessView} from '~/api/account'

/**
 * A product a Discord role opens. It has no licence page of its own, so it leads to the product's
 * page, where its server keys are made, and to the updaters, where its upgrade tokens are.
 */
const {t} = useI18n()

defineProps<{
    access: RoleAccessView
    /** Whether the row sits beneath the product its product is an add-on of. */
    addon?: boolean
}>()
</script>

<template>
  <div
      :class="addon ? 'ml-8' : ''"
      class="flex items-center gap-3 rounded-theme border border-border-light p-3 dark:border-border-dark"
  >
    <ProductMonogram :name="access.productName" size="sm"/>
    <div class="min-w-0 flex-1">
      <NuxtLink :to="`/products/${access.productId}`" class="block truncate font-medium hover:text-primary">
        {{ access.productName }}
      </NuxtLink>
      <div class="flex flex-wrap items-center gap-1.5">
        <NeutralBadge v-if="access.parentId != null">{{ t('common.addon.addon') }}</NeutralBadge>
        <SecondaryBadge v-for="type in access.releaseTypes" :key="type">{{ type }}</SecondaryBadge>
        <MutedText>{{ t('ui.roleAccessRow.viaRole') }}</MutedText>
      </div>
    </div>
    <NuxtLink class="text-sm text-primary hover:underline" to="/account/updaters">
      {{ t('ui.roleAccessRow.keysAndUpdates') }}
    </NuxtLink>
  </div>
</template>
