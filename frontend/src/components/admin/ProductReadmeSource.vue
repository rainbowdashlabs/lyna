/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref, watch} from 'vue'
import {type ProductSummary, setPageSource} from '~/api/admin'
import {getProduct, type KioskProductDetail} from '~/api/kiosk'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * Whether the product page shows the project's GitHub README, and what that page currently shows.
 * A product with no description of its own shows the README anyway, when its URL is a repository.
 */
const props = defineProps<{ guildId: string, product: ProductSummary }>()

const {t} = useI18n()
const readme = ref(props.product.pageReadme ?? false)
const shown = ref<KioskProductDetail | null>(null)
const {busy, error, run} = useAdminAction(() => t('ui.productReadmeSource.couldNotSave'))

const isRepository = computed(() => /^https?:\/\/(www\.)?github\.com\/[\w.-]+\/[\w.-]+\/?$/i.test(props.product.url ?? ''))

async function refresh() {
  shown.value = await getProduct(props.product.id).catch(() => null)
}

watch(readme, async next => {
  await run(() => setPageSource(props.guildId, props.product.id, next))
  await refresh()
})

onMounted(refresh)
</script>

<template>
  <section class="space-y-3 border border-border-light p-4 dark:border-border-dark">
    <label class="flex items-center gap-2 text-sm">
      <CompactToggle v-model="readme" :disabled="busy || !isRepository"/> {{ t('ui.productReadmeSource.useReadme') }}
    </label>
    <MutedText size="xs" tag="p">
      {{ isRepository ? t('ui.productReadmeSource.explain') : t('ui.productReadmeSource.needsRepository') }}
    </MutedText>
    <template v-if="shown?.pageSource === 'README'">
      <MutedText size="xs" tag="p">
        {{ t('ui.productReadmeSource.showing') }}
        <a :href="shown.readmeUrl ?? undefined" class="text-primary underline" rel="noopener" target="_blank">{{ t('ui.productReadmeSource.onGithub') }}</a>
      </MutedText>
      <div class="max-h-[32rem] overflow-y-auto border border-border-light p-4 dark:border-border-dark">
        <ProductDescription :markdown="shown.description"/>
      </div>
    </template>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </section>
</template>
