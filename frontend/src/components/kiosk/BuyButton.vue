/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import type {PurchaseOption} from '~/api/kiosk'

/**
 * Where to buy a product. One platform is a link straight to it; several open a picker naming each,
 * so a buyer chooses where to pay rather than being sent to whichever came first.
 */
const props = defineProps<{ options: PurchaseOption[] }>()

const {t} = useI18n()
const open = ref(false)
const root = ref<HTMLElement | null>(null)
const single = computed(() => props.options.length === 1 ? props.options[0] : null)

const linkClass = 'rounded-theme inline-flex items-center border border-(--border) px-3 py-1.5 text-sm font-medium whitespace-nowrap transition-colors hover:border-secondary hover:bg-secondary/12'

function buyOn(option: PurchaseOption): string {
  return t('ui.buyButton.buyOn', {platform: t(`common.platform.${option.platform}`)})
}

function closeOutside(event: MouseEvent) {
  if (root.value && !root.value.contains(event.target as Node)) open.value = false
}

onMounted(() => document.addEventListener('click', closeOutside))
onBeforeUnmount(() => document.removeEventListener('click', closeOutside))
</script>

<template>
  <a v-if="single" :class="linkClass" :href="single.url" data-testid="buy-button" rel="noopener noreferrer" target="_blank">
    <font-awesome-icon :icon="['fas', 'cart-shopping']" class="mr-1"/>
    {{ buyOn(single) }}
  </a>
  <div v-else ref="root" class="relative inline-block">
    <SecondaryButton :aria-expanded="open" data-testid="buy-button" @click="open = !open">
      <font-awesome-icon :icon="['fas', 'cart-shopping']" class="mr-1"/>
      {{ t('ui.buyButton.buy') }}
      <font-awesome-icon :icon="['fas', 'chevron-down']" class="ml-1"/>
    </SecondaryButton>
    <ul
        v-if="open"
        class="absolute right-0 z-20 mt-1 min-w-full rounded-theme border border-(--border) bg-bg-light py-1 shadow-lg dark:bg-bg-dark"
        data-testid="buy-picker"
    >
      <li v-for="option in options" :key="option.platform">
        <a
            :href="option.url"
            class="block px-3 py-1.5 text-sm whitespace-nowrap transition-colors hover:bg-secondary/12"
            rel="noopener noreferrer"
            target="_blank"
            @click="open = false"
        >
          {{ buyOn(option) }}
        </a>
      </li>
    </ul>
  </div>
</template>
