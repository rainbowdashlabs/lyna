<script lang="ts" setup>
import PrimaryButton from '~/components/button/PrimaryButton.vue'
import ProductMonogram from './ProductMonogram.vue'
import type {KioskProduct} from '~/api/kiosk'

defineProps<{
  product: KioskProduct
}>()

const emit = defineEmits<{
  download: [product: KioskProduct]
}>()
</script>

<template>
  <article
      class="flex h-full flex-col rounded-theme border border-border-light dark:border-border-dark bg-bg-light dark:bg-bg-dark p-4 transition-shadow hover:shadow-lg"
  >
    <header class="flex items-start gap-3">
      <ProductMonogram :name="product.name" />
      <div class="min-w-0 flex-1">
        <h2 class="truncate text-lg font-semibold">
          {{ product.name }}
        </h2>
        <p v-if="product.url" class="truncate text-xs opacity-60">
          <a :href="product.url" target="_blank" rel="noopener noreferrer">project page →</a>
        </p>
      </div>
    </header>
    <footer class="mt-4 flex items-center justify-end gap-2">
      <a
          v-if="product.url"
          :href="product.url"
          target="_blank"
          rel="noopener noreferrer"
          class="inline-flex items-center rounded-theme bg-secondary px-3 py-1.5 text-sm font-medium text-secondary-text hover:bg-secondary-accent"
      >
        <font-awesome-icon :icon="['fab', 'github']" class="mr-1" />
        Project
      </a>
      <PrimaryButton @click="emit('download', product)">
        <font-awesome-icon :icon="['fas', 'download']" class="mr-1" />
        Download
      </PrimaryButton>
    </footer>
  </article>
</template>
